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

/** 작업 하나의 실행 순서만 조율한다. DB 상태 저장과 공급자 자료 규격은 협력 객체가 담당한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisWorker {
    private final AnalysisJobStateService jobStateService;
    private final GeminiService geminiService;
    private final AnalysisResultProcessor resultProcessor;

    /**
     * 선점 -> 공급자 호출 -> 근거 검증 -> 결과 저장 순으로 실행한다.
     * 메서드 전체에 트랜잭션을 열지 않아 네트워크 대기 중 DB 연결·행 잠금을 점유하지 않는다.
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

            // 선점 트랜잭션이 끝난 뒤 호출한다. 슬롯 대기는 취소 인터럽트를 수용한다.
            stage = "provider";
            String raw = geminiService.analyze(claim.ownerId(), resultProcessor.buildPrompt(claim.input()));

            // 공급자 JSON이 정상이어도 원문·설정·요청 옵션과 맞지 않으면 결과로 채택하지 않는다.
            stage = "validation";
            AnalysisData result = resultProcessor.validate(raw, claim.input());

            stage = "completion";
            jobStateService.succeed(jobId, result);
        } catch (Exception error) {
            String message = error instanceof CustomException custom
                    ? custom.getErrorCode().getMessage()
                    : "AI 검토 결과를 처리하지 못했습니다. 다시 시도해 주세요.";

            // 작업 ID와 단계로 진단하되 예외 메시지에 섞일 수 있는 원고·토큰은 로그에 남기지 않는다.
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
