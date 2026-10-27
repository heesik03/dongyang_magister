package com.magister.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.magister.backend.domain.user.security.AuthRequestLimiter;
import com.magister.backend.domain.user.security.AuthRequestLimiter.Permit;
import com.magister.backend.global.config.AuthRateLimitProperties;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.security.AuthRateLimitFilter;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class AuthRequestLimiterTests {
    @Test
    void rejectsExcessAttemptsBeforeAuthenticationAndKeepsApiErrorContract() throws Exception {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.setLoginAttempts(2);
        AuthRateLimitFilter filter =
                new AuthRateLimitFilter(
                        new AuthRequestLimiter(properties), JsonMapper.builder().build());
        AtomicInteger calls = new AtomicInteger();

        for (int index = 0; index < 3; index++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(
                    new MockHttpServletRequest("POST", "/api/user/login"),
                    response,
                    (request, target) -> calls.incrementAndGet());
            if (index == 2) {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(
                                JsonMapper.builder()
                                        .build()
                                        .readTree(response.getContentAsString())
                                        .path("code")
                                        .asString())
                        .isEqualTo("TOO_MANY_REQUESTS");
            }
        }
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void individualWindowExpiresWithoutWaitingForGlobalCleanup() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.setLoginAttempts(1);
        AtomicLong now = new AtomicLong();
        AuthRequestLimiter limiter = new AuthRequestLimiter(properties, now::get);
        now.set(Duration.ofSeconds(50).toNanos());
        limiter.reserve("/api/user/login", "client").close();
        now.set(Duration.ofSeconds(60).toNanos());
        limiter.reserve("/api/user/email/check", "other").close();
        assertThatThrownBy(() -> limiter.reserve("/api/user/login", "client"))
                .isInstanceOf(CustomException.class);

        now.set(Duration.ofSeconds(111).toNanos());
        limiter.reserve("/api/user/login", "client").close();
    }

    @Test
    void heavyRequestsShareCapacityAndReleaseItEvenWhenChainFails() throws Exception {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.setConcurrentHashes(1);
        properties.setSignupAttempts(1);
        AuthRequestLimiter limiter = new AuthRequestLimiter(properties);
        try (Permit permit = limiter.reserve("/api/user/login", "client")) {
            assertThatThrownBy(() -> limiter.reserve("/api/user", "another"))
                    .isInstanceOf(CustomException.class);
            // 이메일 조회에는 고비용 해싱 슬롯이 필요하지 않다.
            limiter.reserve("/api/user/email/check", "another").close();
        }
        // 전역 해싱 슬롯 포화로 거절된 요청은 개별 클라이언트의 시도 횟수를 소진하지 않는다.
        limiter.reserve("/api/user", "another").close();

        AuthRateLimitFilter filter = new AuthRateLimitFilter(limiter, JsonMapper.builder().build());
        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        new MockHttpServletRequest("POST", "/api/user/login"),
                                        new MockHttpServletResponse(),
                                        (request, response) -> {
                                            throw new ServletException();
                                        }))
                .isInstanceOf(ServletException.class);
        limiter.reserve("/api/user/login", "client").close();
    }

    @Test
    void trackedClientCountIsBoundedAndExpiredEntriesCanBeReused() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.setMaxTrackedClients(1);
        AtomicLong now = new AtomicLong();
        AuthRequestLimiter limiter = new AuthRequestLimiter(properties, now::get);
        limiter.reserve("/api/user/login", "first").close();
        assertThatThrownBy(() -> limiter.reserve("/api/user/login", "second"))
                .isInstanceOf(CustomException.class);

        now.set(Duration.ofMinutes(2).toNanos());
        limiter.reserve("/api/user/login", "second").close();
    }
}
