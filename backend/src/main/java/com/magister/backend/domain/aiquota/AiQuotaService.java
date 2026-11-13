package com.magister.backend.domain.aiquota;

import com.magister.backend.domain.aiquota.AiQuotaPolicy.Feature;
import com.magister.backend.domain.aiquota.AiQuotaPolicy.Period;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AiQuotaService {
    private final JdbcTemplate jdbcTemplate;
    private final int sceneLimit;
    private final int characterImageLimit;

    public AiQuotaService(
            JdbcTemplate jdbcTemplate,
            @Value("${scene-board.analysis-daily-limit:10}") int sceneLimit,
            @Value("${character-image.daily-limit:3}") int characterImageLimit) {
        if (sceneLimit < 1 || characterImageLimit < 1) {
            throw new IllegalArgumentException("AI daily limits must be positive");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.sceneLimit = sceneLimit;
        this.characterImageLimit = characterImageLimit;
    }

    /** 새 작업을 저장하는 트랜잭션에서만 호출한다. 재전송 판정은 호출자가 먼저 수행한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void charge(long userId, Feature feature) {
        Period period = AiQuotaPolicy.periodAt(Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO ai_usage_buckets(user_id, feature, period_start, used_count)
                VALUES (?, ?, ?, 0)
                ON DUPLICATE KEY UPDATE used_count = used_count
                """,
                userId,
                feature.name(),
                period.start());

        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE ai_usage_buckets SET used_count = used_count + 1
                        WHERE user_id = ? AND feature = ? AND period_start = ? AND used_count < ?
                        """,
                        userId,
                        feature.name(),
                        period.start(),
                        limit(feature));
        if (updated == 0) {
            throw new AiQuotaExceededException(period.resetAt());
        }
    }

    /** 현재 사용량 조회는 집계 행을 생성하거나 잠그지 않는다. */
    @Transactional(readOnly = true)
    public Usage usage(long userId, Feature feature, Instant now) {
        Period period = AiQuotaPolicy.periodAt(now);
        Integer used =
                jdbcTemplate.query(
                        """
                        SELECT used_count FROM ai_usage_buckets
                        WHERE user_id = ? AND feature = ? AND period_start = ?
                        """,
                        result -> result.next() ? result.getInt(1) : 0,
                        userId,
                        feature.name(),
                        period.start());

        return new Usage(feature, used == null ? 0 : used, limit(feature), period.resetAt());
    }

    private int limit(Feature feature) {
        return switch (feature) {
            case SCENE_ANALYSIS -> sceneLimit;
            case CHARACTER_IMAGE -> characterImageLimit;
        };
    }

    public record Usage(Feature feature, int used, int limit, Instant resetAt) {}
}
