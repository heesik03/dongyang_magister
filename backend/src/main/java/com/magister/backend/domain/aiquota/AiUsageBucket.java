package com.magister.backend.domain.aiquota;

import com.magister.backend.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;
import java.time.LocalDate;

/** JDBC의 원자적 할당량 갱신에 사용하는 복합 키를 정의한다. */
@Entity
@Table(name = "ai_usage_buckets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUsageBucket {
    @EmbeddedId
    private Key id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Embeddable
    public record Key(
            @Column(name = "user_id") Long userId,
            @Column(length = 32) String feature,
            @Column(name = "period_start") LocalDate periodStart)
            implements Serializable {}
}
