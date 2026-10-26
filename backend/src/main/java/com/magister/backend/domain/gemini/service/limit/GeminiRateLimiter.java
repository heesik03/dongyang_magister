package com.magister.backend.domain.gemini.service.limit;

import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.LongSupplier;

/** 프로세스 내 공급자 동시 호출 슬롯과 사용자별 시작 간격을 관리한다. */
@Component
public class GeminiRateLimiter {
    private static final long INTERVAL_NANOS = Duration.ofSeconds(5).toNanos();
    private static final long RETENTION_NANOS = Duration.ofMinutes(10).toNanos();
    private static final long CLEANUP_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();
    private static final int MAX_USERS = 10_000;
    private static final int MAX_ACTIVE = 2;
    private final LongSupplier clock;
    private final Map<Long, Window> windows = new HashMap<>();
    private long lastCleanup;
    private int active;

    private static final class Window {
        private boolean busy;
        private long started;
    }

    @Autowired
    public GeminiRateLimiter() {
        this(System::nanoTime);
    }

    public GeminiRateLimiter(LongSupplier clock) {
        this.clock = clock;
        lastCleanup = clock.getAsLong();
    }

    /** 일반 동기 API는 용량이 없거나 너무 빠른 재호출이면 즉시 429를 반환한다. */
    public synchronized Reservation reserve(Long ownerId) {
        validateOwner(ownerId);
        long now = clock.getAsLong();
        cleanup(now);
        if (!available(ownerId, now)) throw new CustomException(CommonErrorCode.TOO_MANY_REQUESTS);
        return occupy(ownerId, now);
    }

    /** 접수된 비동기 작업은 공급자에 보내기 전에 슬롯을 기다리며 취소 인터럽트를 수용한다. */
    public synchronized Reservation awaitReservation(Long ownerId) {
        validateOwner(ownerId);
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            long now = clock.getAsLong();
            cleanup(now);
            if (available(ownerId, now)) return occupy(ownerId, now);
            try {
                wait(100);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new CancellationException();
            }
        }
    }

    private boolean available(Long ownerId, long now) {
        Window window = windows.get(ownerId);
        return active < MAX_ACTIVE
                && (window == null || (!window.busy && now - window.started >= INTERVAL_NANOS))
                && (window != null || windows.size() < MAX_USERS);
    }

    private Reservation occupy(Long ownerId, long now) {
        Window window = windows.computeIfAbsent(ownerId, ignored -> new Window());
        window.busy = true;
        window.started = now;
        active++;
        return new Reservation(window);
    }

    private void cleanup(long now) {
        if (now - lastCleanup < CLEANUP_INTERVAL_NANOS) return;
        windows.entrySet().removeIf(entry -> !entry.getValue().busy
                && now - entry.getValue().started > RETENTION_NANOS);
        lastCleanup = now;
    }

    private void validateOwner(Long ownerId) {
        if (ownerId == null || ownerId <= 0) throw new CustomException(CommonErrorCode.INVALID_REQUEST);
    }

    public final class Reservation implements AutoCloseable {
        private final Window window;
        private boolean released;

        private Reservation(Window window) {
            this.window = window;
        }

        @Override
        public void close() {
            synchronized (GeminiRateLimiter.this) {
                if (released) return;
                released = true;
                window.busy = false;
                active--;
                GeminiRateLimiter.this.notifyAll();
            }
        }
    }
}
