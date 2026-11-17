package com.magister.backend.domain.user.security;

import com.magister.backend.domain.user.service.support.EmailNormalizer;
import com.magister.backend.global.config.AuthRateLimitProperties;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UrlPathHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** HTTP 인증 시도와 해싱 동시 작업 수를 프로세스 안에서 제한한다. 개인정보는 로그에 남기지 않는다. */
@Component
public class AuthRequestLimiter {
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private final AuthRateLimitProperties properties;
    private final LongSupplier clock;
    private final Semaphore hashingSlots;
    private final Map<String, Window> windows = new HashMap<>();
    private long lastCleanup;
    private long oldestWindowStart;

    private static final class Window {
        private final long started;
        private int attempts;

        private Window(long started) {
            this.started = started;
        }
    }

    @Autowired
    public AuthRequestLimiter(AuthRateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    public AuthRequestLimiter(AuthRateLimitProperties properties, LongSupplier clock) {
        this.properties = properties;
        this.clock = clock;
        hashingSlots = new Semaphore(properties.getConcurrentHashes());
        lastCleanup = clock.getAsLong();
        oldestWindowStart = lastCleanup;
    }

    /** URL 퍼센트 인코딩·중복 슬래시·말미 슬래시를 정규화하여 Spring MVC 라우팅 경로와 기준을 맞춘다. */
    public static String normalizePath(HttpServletRequest request) {
        String path = PATH_HELPER.getPathWithinApplication(request);
        return normalizePath(path);
    }

    public static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }
        String normalized = rawPath.replaceAll("/{2,}", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public boolean appliesTo(HttpServletRequest request) {
        return appliesTo(request.getMethod(), normalizePath(request));
    }

    public boolean appliesTo(String method, String path) {
        return "POST".equalsIgnoreCase(method) && limit(normalizePath(path)) > 0;
    }

    /** 신뢰되지 않은 X-Forwarded-For 대신 서버가 확인한 remoteAddr를 사용한다. */
    public Permit reserve(HttpServletRequest request) {
        return reserve(normalizePath(request), request.getRemoteAddr());
    }

    public Permit reserve(String path, String remoteAddress) {
        String normalizedPath = normalizePath(path);
        boolean hashing = !normalizedPath.equals("/api/user/email/check");
        if (hashing) {
            Permit hashingPermit = acquireHashingSlot();
            boolean recorded = false;
            try {
                recordAttempt(normalizedPath + ":" + remoteAddress, limit(normalizedPath));
                recorded = true;
                return hashingPermit;
            } finally {
                if (!recorded) {
                    hashingPermit.close();
                }
            }
        }
        recordAttempt(normalizedPath + ":" + remoteAddress, limit(normalizedPath));
        return new Permit(false);
    }

    /** 필터를 우회한 직접 호출이나 인증 실행 지점에서도 동시 해싱 상한을 보장한다. */
    public Permit acquireHashingSlot() {
        if (!hashingSlots.tryAcquire()) {
            throw new CustomException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        return new Permit(true);
    }

    /** IP 제한과 별도로 로그인 대상 계정(이메일) 기준 시도 횟수를 제한한다. */
    public void recordLoginAccountAttempt(String email) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        if (normalizedEmail.isEmpty()) {
            return;
        }
        recordAttempt("account:" + normalizedEmail, properties.getLoginAccountAttempts());
    }

    private synchronized void recordAttempt(String key, int maxAttempts) {
        if (maxAttempts <= 0) {
            return;
        }
        long now = clock.getAsLong();
        long windowNanos = properties.getWindow().toNanos();
        boolean periodicCleanup = now - lastCleanup >= windowNanos;
        boolean capacityCleanup =
                windows.size() >= properties.getMaxTrackedClients()
                        && now - oldestWindowStart >= windowNanos;
        if (periodicCleanup || capacityCleanup) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().started >= windowNanos);
            lastCleanup = now;
            oldestWindowStart = now;
            for (Window existing : windows.values()) {
                if (existing.started < oldestWindowStart) {
                    oldestWindowStart = existing.started;
                }
            }
        }

        Window window = windows.get(key);
        if (window != null && now - window.started >= windowNanos) {
            // 전체 만료 정리 주기와 각 사용자의 실제 제한 창은 독립적으로 만료되어야 한다.
            window = new Window(now);
            windows.put(key, window);
        }
        if (window == null) {
            if (windows.size() >= properties.getMaxTrackedClients()) {
                throw new CustomException(CommonErrorCode.TOO_MANY_REQUESTS);
            }
            if (windows.isEmpty() || now < oldestWindowStart) {
                oldestWindowStart = now;
            }
            window = new Window(now);
            windows.put(key, window);
        }
        if (window.attempts >= maxAttempts) {
            throw new CustomException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        window.attempts++;
    }

    private int limit(String path) {
        return switch (path) {
            case "/api/user/login" -> properties.getLoginAttempts();
            case "/api/user" -> properties.getSignupAttempts();
            case "/api/user/email/check" -> properties.getEmailChecks();
            default -> 0;
        };
    }

    public final class Permit implements AutoCloseable {
        private final boolean hashing;
        private final AtomicBoolean released = new AtomicBoolean();

        private Permit(boolean hashing) {
            this.hashing = hashing;
        }

        @Override
        public void close() {
            if (hashing && released.compareAndSet(false, true)) {
                hashingSlots.release();
            }
        }
    }
}
