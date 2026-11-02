package com.magister.backend.domain.timeline.service;

import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/** 설정 검토와 제한된 실행기를 공유하되 작업·취소 등록은 분리한다. */
@Component
@RequiredArgsConstructor
public class ExtractionDispatcher {
    private final ThreadPoolExecutor analysisExecutor;
    private final ExtractionWorker extractionWorker;
    private final ExtractionStateService extractionStateService;
    private final Map<String, FutureTask<Void>> tasks = new ConcurrentHashMap<>();

    /** 커밋된 작업만 제출한다. 중복 전달은 기존 Future를 유지한다. */
    public void submit(String id) {
        FutureTask<Void> task =
                new FutureTask<>(
                        () -> {
                            extractionWorker.execute(id);
                            return null;
                        }) {
                    @Override
                    public void run() {
                        try {
                            super.run();
                        } finally {
                            tasks.remove(id, this);
                        }
                    }
                };
        if (tasks.putIfAbsent(id, task) != null) {
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
        FutureTask<Void> task = tasks.get(id);
        if (task != null) {
            analysisExecutor.remove(task);
            task.cancel(true);
            tasks.remove(id, task);
        }
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void maintain() {
        extractionStateService.expire();
    }

    @PreDestroy
    public void close() {
        for (String id : tasks.keySet()) {
            extractionStateService.fail(id);
            cancel(id);
        }
    }
}
