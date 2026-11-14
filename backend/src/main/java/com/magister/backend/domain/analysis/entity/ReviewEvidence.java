package com.magister.backend.domain.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** 검증한 분석 결과의 원문 위치를 수정 없이 보존한다. */
@Entity
@Table(
        name = "review_evidences",
        indexes = {
            @Index(name = "ix_evidence_job", columnList = "job_id,id"),
            @Index(name = "ix_evidence_review", columnList = "review_id,id")
        })
@Check(constraints = "start_offset >= 0 AND end_offset > start_offset")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewEvidence {
    @Column(name = "job_id", nullable = false, length = 36, updatable = false)
    private String jobId;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false, length = 36, updatable = false)
    private String reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumns({
        @JoinColumn(name = "job_id", referencedColumnName = "job_id", insertable = false, updatable = false),
        @JoinColumn(name = "review_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private ReviewCandidate review;

    @Column(name = "source_id", nullable = false, updatable = false)
    private Long sourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumns({
        @JoinColumn(name = "job_id", referencedColumnName = "job_id", insertable = false, updatable = false),
        @JoinColumn(name = "source_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
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
        this.reviewId = review.getId();
        this.source = source;
        this.sourceId = source.getId();
        this.quote = quote;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.explanation = explanation;
    }
}
