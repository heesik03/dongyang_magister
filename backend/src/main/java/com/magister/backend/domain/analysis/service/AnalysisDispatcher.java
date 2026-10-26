package com.magister.backend.domain.analysis.service;

import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** DB 커밋 후의 제출·취소·종료와 로컬 실행 객체의 생명주기를 관리한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisDispatcher {
    private final ThreadPoolExecutor analysisExecutor;
    private final AnalysisWorker analysisWorker;
    private final AnalysisJobStateService jobStateService;
    private final Map<String, TrackedTask> tasks = new ConcurrentHashMap<>();

    /**
     * 커밋된 작업 ID를 실행기에 제출하고 동일 ID의 로컬 중복 제출은 생략한다.
     * 큐 포화·종료로 거절되면 등록을 제거하고 별도 트랜잭션으로 FAILED를 확정한다.
     * 엔티티를 람다로 넘기지 않아 다른 스레드가 기존 영속성 컨텍스트에 접근하지 않게 한다.
     */
    public void submit(String jobId) {
        TrackedTask task = new TrackedTask(jobId);
        if (tasks.putIfAbsent(jobId, task) != null) {
            return;
        }

        try {
            analysisExecutor.execute(task);
        } catch (RejectedExecutionException error) {
            tasks.remove(jobId, task);
            jobStateService.fail(jobId, "AI 대기열이 가득 찼습니다. 다시 시도해 주세요.");
        }
    }

    /**
     * 대기 작업은 실제 큐에서 제거하고 실행 중 작업에는 인터럽트를 요청한다.
     * SDK가 인터럽트를 무시하면 호출이 끝날 때까지 실행 등록과 공급자 슬롯을 유지한다.
     * DB 취소가 커밋된 뒤 호출해야 워커의 실패 저장보다 CANCELLED가 먼저 확정된다.
     */
    public void cancel(String jobId) {
        TrackedTask task = tasks.get(jobId);
        if (task == null) {
            return;
        }

        analysisExecutor.remove(task);
        task.cancel(true);
    }

    /** 실제 호출이 끝나지 않은 취소 작업도 포함한 로컬 실행 스냅샷이다. */
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
                log.warn(
                        "Shutdown state save failed: jobId={}, type={}",
                        jobId,
                        error.getClass().getSimpleName());
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

    /** 살아 있는 로컬 작업의 lease와 다른 서버에서 발생한 취소를 반영한다. */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void maintain() {
        jobStateService.maintain(activeJobIds()).forEach(this::cancel);
    }

    /** Future의 논리적 완료와 실제 실행 종료가 다른 취소 상황을 구분한다. */
    private final class TrackedTask extends FutureTask<Void> {
        private final String jobId;
        private volatile boolean started;

        private TrackedTask(String jobId) {
            super(
                    () -> {
                        analysisWorker.execute(jobId);
                        return null;
                    });
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
            // 시작 전 취소는 run()이 호출되지 않을 수 있으므로 여기서 등록을 정리한다.
            if (!started) {
                tasks.remove(jobId, this);
            }
        }
    }
}
