package com.magister.backend.domain.decision.entity;

import com.magister.backend.domain.analysis.entity.ReviewCandidate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 판단 버전과 그 적용 범위는 추가만 허용하고 과거 값을 수정하지 않는다. */
@Entity
@Table(name = "review_decisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewDecision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private ReviewCandidate review;

    @Column(nullable = false, updatable = false)
    private long decisionVersion;

    @Column(nullable = false, length = 20, updatable = false)
    private String status;

    @Column(nullable = false, columnDefinition = "LONGTEXT", updatable = false)
    private String reason;

    @Column(nullable = false, length = 64, updatable = false)
    private String mutationId;

    @Column(nullable = false, columnDefinition = "LONGTEXT", updatable = false)
    private String requestBody;

    @Column(nullable = false, length = 64, updatable = false)
    private String basisHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public ReviewDecision(
            ReviewCandidate review,
            long decisionVersion,
            String status,
            String reason,
            String mutationId,
            String requestBody,
            String basisHash) {
        this.review = review;
        this.decisionVersion = decisionVersion;
        this.status = status;
        this.reason = reason;
        this.mutationId = mutationId;
        this.requestBody = requestBody;
        this.basisHash = basisHash;
        createdAt = Instant.now();
    }
}
