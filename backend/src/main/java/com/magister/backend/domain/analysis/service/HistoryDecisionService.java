package com.magister.backend.domain.analysis.service;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class HistoryDecisionService {
    private final HistoryFindingDecisionRepository decisions;
    private final AnalysisJobRepository jobs;
    private final WorkAccessService access;
    private final AnalysisSnapshotService snapshots;
    private final ObjectMapper mapper;

    private String claimKey(Finding finding) {
        // 대상만 같은 다른 주장이나 시대에 작가의 예외를 확대하지 않는다.
        String identity = mapper.writeValueAsString(List.of(finding.target().strip(),
                finding.sceneTime().strip(), finding.assertion().strip()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public Map<Integer, HistoryDecision> summaries(AnalysisJob job, HistoryJobReport report) {
        Map<Integer, HistoryDecision> result = new HashMap<>();
        if (report == null) return result;
        for (int i = 0; i < report.findings().size(); i++) {
            int index = i;
            decisions.findByJob_IdAndFindingIndex(job.getId(), index)
                    .map(HistoryFindingDecision::decision)
                    .or(() -> decisions.findFirstByJob_Work_IdAndClaimKeyAndJob_CreatedAtBeforeOrderByJob_CreatedAtDescIdDesc(
                                    job.getWork().getId(), claimKey(report.findings().get(index)), job.getCreatedAt())
                            .map(HistoryFindingDecision::decision)
                            .filter(previous -> previous.choice() == HistoryDecision.Choice.INTENDED))
                    .filter(value -> value.choice() != HistoryDecision.Choice.WITHDRAWN)
                    .ifPresent(value -> result.put(index, value));
        }
        return result;
    }

    @Transactional
    public HistoryDecision save(Long ownerId, Long workId, String jobId, int index, HistoryDecision input) {
        access.lockOwned(ownerId, workId);
        AnalysisJob job = jobs.findOwned(jobId, workId, ownerId)
                .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
        HistoryJobReport report = job.getHistoryReport() == null ? null
                : mapper.readValue(job.getHistoryReport(), HistoryJobReport.class);
        if (report == null || index < 0 || index >= report.findings().size()) {
            throw new CustomException(AnalysisErrorCode.HISTORY_FINDING_NOT_FOUND);
        }
        if (snapshots.isStale(workId, job.getInput())) {
            throw new CustomException(AnalysisErrorCode.HISTORY_DECISION_STALE);
        }
        HistoryFindingDecision row = decisions.findByJob_IdAndFindingIndex(jobId, index)
                .orElseGet(() -> new HistoryFindingDecision(job, index, claimKey(report.findings().get(index))));
        row.record(input);
        return decisions.save(row).decision();
    }
}
