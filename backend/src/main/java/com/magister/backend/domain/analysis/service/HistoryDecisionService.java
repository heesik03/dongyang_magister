package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.ReviewConverter;
import com.magister.backend.domain.analysis.dto.HistoryDecision;
import com.magister.backend.domain.analysis.dto.HistoryJobReport;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.HistoryFindingDecision;
import com.magister.backend.domain.analysis.error.AnalysisErrorCode;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.repository.HistoryFindingDecisionRepository;
import com.magister.backend.domain.history.dto.HistoryCheck.Finding;
import com.magister.backend.domain.work.error.WorkErrorCode;
import com.magister.backend.domain.work.service.WorkAccessService;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoryDecisionService {
    private final HistoryFindingDecisionRepository decisionRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final WorkAccessService workAccessService;
    private final AnalysisSnapshotService snapshotService;
    private final ReviewConverter reviewConverter;
    private final ObjectMapper objectMapper;

    private String claimKey(Finding finding) {
        // 대상만 같은 다른 주장이나 시대에 작가의 예외를 확대하지 않는다.
        String identity =
                objectMapper.writeValueAsString(
                        List.of(
                                finding.target().strip(),
                                finding.sceneTime().strip(),
                                finding.assertion().strip()));
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * 역사 고증 항목별 현재 판단과 같은 작품의 이전 '의도된 설정' 판단을 일괄 매핑한다.
     * 철회된 판단은 제외하며, 항목별 개별 조회(N+1) 대신 작업·클레임 키 단위로 한 번에 읽는다.
     */
    @Transactional(readOnly = true)
    public Map<Integer, HistoryDecision> summaries(AnalysisJob job, HistoryJobReport report) {
        Map<Integer, HistoryDecision> result = new HashMap<>();
        if (report == null || report.findings().isEmpty()) {
            return result;
        }

        Map<Integer, HistoryFindingDecision> currentByIndex =
                decisionRepository.findByJob_IdOrderByFindingIndexAsc(job.getId()).stream()
                        .collect(
                                Collectors.toMap(
                                        HistoryFindingDecision::getFindingIndex,
                                        Function.identity()));
        List<String> keys = report.findings().stream().map(this::claimKey).toList();
        Map<String, HistoryFindingDecision> previousByClaimKey = new HashMap<>();
        for (HistoryFindingDecision previous :
                decisionRepository
                        .findByJob_Work_IdAndClaimKeyInAndJob_CreatedAtBeforeOrderByJob_CreatedAtDescIdDesc(
                                job.getWork().getId(), keys, job.getCreatedAt())) {
            previousByClaimKey.putIfAbsent(previous.getClaimKey(), previous);
        }

        for (int index = 0; index < report.findings().size(); index++) {
            HistoryFindingDecision current = currentByIndex.get(index);
            HistoryDecision resolved = null;
            if (current != null) {
                resolved = reviewConverter.toHistoryDecision(current);
            } else {
                HistoryFindingDecision previous = previousByClaimKey.get(keys.get(index));
                if (previous != null
                        && previous.getChoice() == HistoryDecision.Choice.INTENDED) {
                    resolved = reviewConverter.toHistoryDecision(previous);
                }
            }
            if (resolved != null && resolved.choice() != HistoryDecision.Choice.WITHDRAWN) {
                result.put(index, resolved);
            }
        }
        return result;
    }

    /**
     * 본인 작품의 역사 고증 항목에 작가 판단과 사유를 기록한다.
     * 작품 잠금 아래에서 대상 회차 원고의 변경 여부를 확인하고, 원고가 수정된 경우 409로 재검토를 유도한다.
     */
    @Transactional
    public HistoryDecision save(
            Long ownerId, Long workId, String jobId, int index, HistoryDecision input) {
        workAccessService.lockOwned(ownerId, workId);
        AnalysisJob job =
                analysisJobRepository
                        .findOwned(jobId, workId, ownerId)
                        .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));

        HistoryJobReport report =
                job.getHistoryReport() == null
                        ? null
                        : objectMapper.readValue(job.getHistoryReport(), HistoryJobReport.class);
        if (report == null || index < 0 || index >= report.findings().size()) {
            throw new CustomException(AnalysisErrorCode.HISTORY_FINDING_NOT_FOUND);
        }
        if (snapshotService.isTargetChapterStale(workId, job.getInput())) {
            throw new CustomException(AnalysisErrorCode.HISTORY_DECISION_STALE);
        }

        HistoryFindingDecision row =
                decisionRepository
                        .findByJob_IdAndFindingIndex(jobId, index)
                        .orElseGet(
                                () ->
                                        new HistoryFindingDecision(
                                                job, index, claimKey(report.findings().get(index))));
        row.record(input);
        return reviewConverter.toHistoryDecision(decisionRepository.save(row));
    }
}
