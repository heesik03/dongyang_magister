package com.magister.backend.domain.timeline.service;

import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/** 설정 검토와 제한된 실행기를 공유하되 작업·취소 등록은 분리한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExtractionDispatcher {
    private static final int MAX_PRE_CANCELLED = 1_000;

    private final ThreadPoolExecutor analysisExecutor;
    private final ExtractionWorker extractionWorker;
    private final ExtractionStateService extractionStateService;
    private final Map<String, TrackedExtractionTask> tasks = new ConcurrentHashMap<>();
    private final Set<String> preCancelledIds =
            Collections.synchronizedSet(
                    Collections.newSetFromMap(
                            new LinkedHashMap<String, Boolean>() {
                                @Override
                                protected boolean removeEldestEntry(
                                        Map.Entry<String, Boolean> eldest) {
                                    return size() > MAX_PRE_CANCELLED;
                                }
                            }));

    /** 커밋된 작업만 제출한다. 중복 전달은 기존 Future를 유지하고 선취소된 작업은 건너뛴다. */
    public void submit(String id) {
        if (preCancelledIds.remove(id)) {
            return;
        }
        TrackedExtractionTask task = new TrackedExtractionTask(id);
        if (tasks.putIfAbsent(id, task) != null) {
            return;
        }
        if (preCancelledIds.remove(id)) {
            tasks.remove(id, task);
            return;
        }
        try {
            analysisExecutor.execute(task);
        } catch (RejectedExecutionException error) {
            tasks.remove(id, task);
            extractionStateService.fail(id);
        }
    }

    /** DB 취소 확정 후 인터럽트를 요청한다. 공급자 과금 중단은 보장하지 않는다. */
    public void cancel(String id) {
        preCancelledIds.add(id);
        TrackedExtractionTask task = tasks.get(id);
        if (task == null) {
            return;
        }
        preCancelledIds.remove(id);
        analysisExecutor.remove(task);
        task.cancel(true);
    }

    /** 현재 실행기에 등록된 로컬 사건 추출 작업 ID 목록을 반환한다. */
    public List<String> activeIds() {
        return List.copyOf(tasks.keySet());
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void maintain() {
        extractionStateService.maintain(activeIds()).forEach(this::cancel);
    }

    @PreDestroy
    public void close() {
        for (String id : activeIds()) {
            try {
                extractionStateService.fail(id);
            } catch (RuntimeException error) {
                log.warn(
                        "Shutdown extraction state save failed: id={}, type={}",
                        id,
                        error.getClass().getSimpleName());
            }
            cancel(id);
        }
    }

    private final class TrackedExtractionTask extends FutureTask<Void> {
        private final String id;
        private volatile boolean started;

        private TrackedExtractionTask(String id) {
            super(
                    () -> {
                        extractionWorker.execute(id);
                        return null;
                    });
            this.id = id;
        }

        @Override
        public void run() {
            started = true;
            try {
                super.run();
            } finally {
                tasks.remove(id, this);
            }
        }

        @Override
        protected void done() {
            if (!started) {
                tasks.remove(id, this);
            }
        }
    }
}
