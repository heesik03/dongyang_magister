package com.magister.backend.domain.analysis.service.execution;

import com.magister.backend.domain.analysis.service.state.AnalysisJobStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

@Component
@RequiredArgsConstructor
public class AnalysisDispatcher {
    private final ThreadPoolExecutor analysisExecutor;
    private final AnalysisWorker analysisWorker;
    private final AnalysisJobStateService jobStateService;

    /** 접수 커밋 후 호출한다. 거절도 독립 트랜잭션으로 실패 처리한다. */
    public void submit(String jobId) {
        try {
            analysisExecutor.execute(() -> analysisWorker.execute(jobId));
        } catch (RejectedExecutionException error) {
            jobStateService.fail(jobId, "AI 대기열이 가득 찼습니다. 다시 시도해 주세요.");
        }
    }
}
