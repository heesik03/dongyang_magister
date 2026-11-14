package com.magister.backend.domain.aiquota;

import com.magister.backend.domain.aiquota.AiQuotaPolicy.Feature;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest(properties = {"character-image.daily-limit=1"})
class AiQuotaServiceTests {
    @Autowired private AiQuotaService quota;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentRequestsCannotExceedLimitAndUsersAreIndependent() throws Exception {
        long first = createUser();
        long second = createUser();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> one = executor.submit(() -> chargeAfter(start, first));
            Future<Boolean> two = executor.submit(() -> chargeAfter(start, first));
            start.countDown();
            assertThat(one.get(10, TimeUnit.SECONDS)).isNotEqualTo(two.get(10, TimeUnit.SECONDS));

            charge(second, Feature.CHARACTER_IMAGE);
            assertThat(quota.usage(first, Feature.CHARACTER_IMAGE, Instant.now()).used()).isEqualTo(1);
            assertThat(quota.usage(second, Feature.CHARACTER_IMAGE, Instant.now()).used()).isEqualTo(1);
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", first, second);
        }
    }

    @Test
    void admissionFailureRollsBackUsageAndSeoulMidnightDeterminesReset() {
        long userId = createUser();
        try {
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                quota.charge(userId, Feature.CHARACTER_IMAGE);
                throw new IllegalStateException("simulated job insert failure");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(quota.usage(userId, Feature.CHARACTER_IMAGE, Instant.now()).used()).isZero();
            assertThat(AiQuotaPolicy.periodAt(Instant.parse("2026-11-10T14:59:59Z")).resetAt())
                    .isEqualTo(Instant.parse("2026-11-10T15:00:00Z"));
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE id=?", userId);
        }
    }

    private boolean chargeAfter(CountDownLatch start, long userId) throws InterruptedException {
        start.await();
        try {
            charge(userId, Feature.CHARACTER_IMAGE);
            return true;
        } catch (AiQuotaExceededException exception) {
            return false;
        }
    }

    private void charge(long userId, Feature feature) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> quota.charge(userId, feature));
    }

    private long createUser() {
        String email = UUID.randomUUID() + "@example.test";
        jdbcTemplate.update("INSERT INTO users(email,name,password_hash) VALUES (?, ?, ?)", email, "테스트", "test");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
    }
}
