package com.magister.backend.domain.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 검증한 분석 결과의 원문 위치를 수정 없이 보존한다. */
@Entity
@Table(name = "review_candidates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewCandidate {
    @Id
    @Column(length = 36, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, updatable = false)
    private AnalysisJob job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_source_id", nullable = false, updatable = false)
    private AnalysisSource targetSource;

    @Column(nullable = false, length = 20, updatable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "LONGTEXT", updatable = false)
    private String quote;

    @Column(nullable = false, updatable = false)
    private int startOffset;

    @Column(nullable = false, updatable = false)
    private int endOffset;

    @Column(nullable = false, columnDefinition = "LONGTEXT", updatable = false)
    private String reason;

    @Column(nullable = false, columnDefinition = "LONGTEXT", updatable = false)
    private String suggestion;

    public ReviewCandidate(
            String id,
            AnalysisJob job,
            AnalysisSource targetSource,
            String type,
            String quote,
            int startOffset,
            int endOffset,
            String reason,
            String suggestion) {
        this.id = id;
        this.job = job;
        this.targetSource = targetSource;
        this.type = type;
        this.quote = quote;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.reason = reason;
        this.suggestion = suggestion;
    }
}
