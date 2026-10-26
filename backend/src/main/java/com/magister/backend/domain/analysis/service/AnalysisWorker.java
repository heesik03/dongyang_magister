package com.magister.backend.domain.analysis.service;

import com.magister.backend.domain.analysis.service.AnalysisJobStateService.Claim;
import com.magister.backend.domain.analysis.service.support.AnalysisResultProcessor;
import com.magister.backend.domain.gemini.service.GeminiService;
import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisWorker {
    private final AnalysisJobStateService jobStateService;
    private final GeminiService geminiService;
    private final AnalysisResultProcessor resultProcessor;

    /** 외부 통신을 트랜잭션 밖에서 실행하며 선점·해석 실패도 상태에 기록한다. */
    public void execute(String jobId) {
        String stage = "claim";

        try {
            Optional<Claim> optionalClaim = jobStateService.claim(jobId);
            if (optionalClaim.isEmpty()) {
                return;
            }

            Claim claim = optionalClaim.get();

            stage = "provider";
            String raw = geminiService.analyze(claim.ownerId(), resultProcessor.buildPrompt(claim.input()));

            stage = "validation";
            AnalysisData result = resultProcessor.validate(raw, claim.input());

            stage = "completion";
            jobStateService.succeed(jobId, result);
        } catch (Exception error) {
            String message = error instanceof CustomException custom
                    ? custom.getErrorCode().getMessage()
                    : "AI 검토 결과를 처리하지 못했습니다. 다시 시도해 주세요.";

            log.warn("Analysis job failed: jobId={}, stage={}, type={}",
                    jobId, stage, error.getClass().getSimpleName());

            try {
                jobStateService.fail(jobId, message);
            } catch (RuntimeException persistenceError) {
                // 원고와 공급자 예외 전문은 기록하지 않는다. 복구 작업이 미완료 상태를 정리한다.
                log.error("Analysis failure could not be saved: jobId={}, type={}",
                        jobId, persistenceError.getClass().getSimpleName());
                throw persistenceError;
            }
        }
    }
}
