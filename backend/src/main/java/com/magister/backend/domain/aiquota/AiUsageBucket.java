package com.magister.backend.domain.aiquota;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/** JDBC의 원자적 할당량 갱신에 사용하는 복합 키를 정의한다. 사용자별(user_id > 0) 및 서비스 전체(user_id = 0) 집계를 함께 저장한다. */
@Entity
@Table(name = "ai_usage_buckets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUsageBucket {
    @EmbeddedId
    private Key id;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Embeddable
    public record Key(
            @Column(name = "user_id") Long userId,
            @Column(length = 32) String feature,
            @Column(name = "period_start") LocalDate periodStart)
            implements Serializable {}
}
