package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.converter.ReviewConverter;
import com.magister.backend.domain.analysis.dto.HistoryJobReport;
import com.magister.backend.domain.analysis.dto.HistoryJobReport.Status;
import com.magister.backend.domain.analysis.dto.ValidatedAnalysis;
import com.magister.backend.domain.analysis.service.AnalysisJobStateService.Claim;
import com.magister.backend.domain.analysis.service.support.AnalysisResultProcessor;
import com.magister.backend.domain.gemini.service.GeminiService;
import com.magister.backend.domain.history.dto.HistoryCheck;
import com.magister.backend.domain.history.service.HistoryCheckService;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;

/** 작업 하나의 실행 순서만 조율한다. DB 상태 저장과 공급자 자료 규격은 협력 객체가 담당한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisWorker {
    private final AnalysisJobStateService jobStateService;
    private final GeminiService geminiService;
    private final AnalysisResultProcessor resultProcessor;
    private final AnalysisConverter converter;
    private final ReviewConverter reviewConverter;
    private final HistoryCheckService historyService;

    /**
     * 선점 -> 공급자 호출 -> 근거 검증 -> 결과 저장 순으로 실행한다. 메서드 전체에 트랜잭션을 열지 않아 네트워크 대기 중 DB 연결·행 잠금을 점유하지 않는다.
     * 첫 선점 단계부터 예외 처리하며, 실패 기록도 불가능하면 lease 만료 복구가 뒤처리한다.
     */
    public void execute(String jobId) {
        String stage = "claim";

        try {
            Optional<Claim> optionalClaim = jobStateService.claim(jobId);
            if (optionalClaim.isEmpty()) {
                return;
            }

            Claim claim = optionalClaim.get();

            stage = "review";
            if (Boolean.TRUE.equals(claim.input().historical())) {
                reviewWithHistory(jobId, claim);
            } else {
                jobStateService.succeed(jobId, standardReview(claim));
            }
        } catch (Exception error) {
            String message =
                    error instanceof CustomException custom
                            ? custom.getErrorCode().getMessage()
                            : "AI 검토 결과를 처리하지 못했습니다. 다시 시도해 주세요.";

            // 작업 ID와 단계로 진단하되 예외 메시지에 섞일 수 있는 원고·토큰은 로그에 남기지 않는다.
            log.warn(
                    "Analysis job failed: jobId={}, stage={}, type={}",
                    jobId,
                    stage,
                    error.getClass().getSimpleName());

            try {
                jobStateService.fail(jobId, message);
            } catch (RuntimeException persistenceError) {
                // 원고와 공급자 예외 전문은 기록하지 않는다. 복구 작업이 미완료 상태를 정리한다.
                log.error(
                        "Analysis failure could not be saved: jobId={}, type={}",
                        jobId,
                        persistenceError.getClass().getSimpleName());
                throw persistenceError;
            }
        }
    }

    private ValidatedAnalysis standardReview(Claim claim) {
        String raw =
                geminiService.analyze(
                        claim.ownerId(), resultProcessor.buildPrompt(claim.input()));
        return resultProcessor.validateDetailed(raw, claim.input());
    }

    private void reviewWithHistory(String jobId, Claim claim) {
        ValidatedAnalysis result =
                new ValidatedAnalysis(converter.toResult(claim.input(), List.of()), List.of());
        Status standard = Status.NOT_REQUESTED;
        if (claim.input().consistency() || claim.input().motivation()) {
            try {
                result = standardReview(claim);
                standard = Status.COMPLETED;
            } catch (CancellationException cancelled) {
                throw cancelled;
            } catch (RuntimeException failure) {
                log.warn(
                        "Standard review failed: jobId={}, type={}",
                        jobId,
                        failure.getClass().getSimpleName());
                standard = Status.FAILED;
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }

        List<HistoryCheck.Finding> findings = List.of();
        Status historical;
        try {
            findings =
                    historyService.checkJob(claim.ownerId(), claim.input().chapter().content());
            long failed =
                    findings.stream()
                            .filter(item -> item.status() == HistoryCheck.Status.LOOKUP_FAILED)
                            .count();
            if (failed == 0) {
                historical = Status.COMPLETED;
            } else {
                historical = failed == findings.size() ? Status.FAILED : Status.PARTIAL;
            }
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException failure) {
            log.warn(
                    "Historical review failed: jobId={}, type={}",
                    jobId,
                    failure.getClass().getSimpleName());
            historical = Status.FAILED;
        }
        HistoryJobReport report =
                reviewConverter.toHistoryJobReport(
                        claim.input().chapter().revision(), historical, standard, findings);
        jobStateService.succeed(jobId, result, report);
    }
}
