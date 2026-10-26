package com.magister.backend.domain.analysis.service.execution;

import com.magister.backend.domain.analysis.service.state.AnalysisJobStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisDispatcher {
    private final ThreadPoolExecutor analysisExecutor;
    private final AnalysisWorker analysisWorker;
    private final AnalysisJobStateService jobStateService;
    private final Map<String, TrackedTask> tasks = new ConcurrentHashMap<>();

    /** 접수 커밋 후 호출한다. 거절도 독립 트랜잭션으로 실패 처리한다. */
    public void submit(String jobId) {
        TrackedTask task = new TrackedTask(jobId);
        if (tasks.putIfAbsent(jobId, task) != null) return;
        try {
            analysisExecutor.execute(task);
        } catch (RejectedExecutionException error) {
            tasks.remove(jobId, task);
            jobStateService.fail(jobId, "AI 대기열이 가득 찼습니다. 다시 시도해 주세요.");
        }
    }

    /** 대기는 큐에서 제거하고 실행 중 작업에는 인터럽트를 요청한다. 공급자 중단을 보장하지는 않는다. */
    public void cancel(String jobId) {
        TrackedTask task = tasks.get(jobId);
        if (task == null) return;
        analysisExecutor.remove(task);
        task.cancel(true);
    }

    public List<String> activeJobIds() {
        return List.copyOf(tasks.keySet());
    }

    /** 종료 시 자기 실행기의 작업만 실패 처리한다. 실패 기록이 불가능하면 lease 복구에 맡긴다. */
    @PreDestroy
    public void close() {
        for (String jobId : activeJobIds()) {
            try {
                jobStateService.fail(jobId, "서버가 종료되었습니다. 다시 요청해 주세요.");
            } catch (RuntimeException error) {
                log.warn("Shutdown state save failed: jobId={}, type={}", jobId, error.getClass().getSimpleName());
            }
            cancel(jobId);
        }
        analysisExecutor.shutdownNow();
        try {
            analysisExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private final class TrackedTask extends FutureTask<Void> {
        private final String jobId;
        private volatile boolean started;

        private TrackedTask(String jobId) {
            super(() -> { analysisWorker.execute(jobId); return null; });
            this.jobId = jobId;
        }

        @Override
        public void run() {
            started = true;
            try {
                super.run();
            } finally {
                // cancel은 Future를 즉시 완료시키므로 실제 호출이 끝나는 시점까지 실행 소유권을 유지한다.
                tasks.remove(jobId, this);
            }
        }

        @Override
        protected void done() {
            if (!started) tasks.remove(jobId, this);
        }
    }
}
