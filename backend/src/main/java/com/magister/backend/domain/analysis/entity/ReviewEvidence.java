package com.magister.backend.domain.analysis.entity;

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

/** 검증한 분석 결과의 원문 위치를 수정 없이 보존한다. */
@Entity
@Table(name = "review_evidences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewEvidence {
    @Column(name = "job_id", nullable = false, updatable = false)
    private String jobId;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private ReviewCandidate review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false, updatable = false)
    private AnalysisSource source;

    @Column(nullable = false, columnDefinition = "LONGTEXT", updatable = false)
    private String quote;

    @Column(nullable = false, updatable = false)
    private int startOffset;

    @Column(nullable = false, updatable = false)
    private int endOffset;

    @Column(nullable = false, columnDefinition = "LONGTEXT", updatable = false)
    private String explanation;

    public ReviewEvidence(
            ReviewCandidate review,
            AnalysisSource source,
            String quote,
            int startOffset,
            int endOffset,
            String explanation) {
        this.jobId = review.getJob().getId();
        this.review = review;
        this.source = source;
        this.quote = quote;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.explanation = explanation;
    }
}
