package com.magister.backend.domain.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** 검증한 분석 결과의 원문 위치와 검토 사유·수정 방향을 보존한다. */
@Entity
@Table(
        name = "review_candidates",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_review_job_id", columnNames = {"id", "job_id"})
        })
@Check(constraints = "type IN ('설정 충돌 후보','설명 보완') AND start_offset >= 0 AND end_offset > start_offset")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewCandidate {
    @Id
    @Column(length = 36, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "job_id", nullable = false, updatable = false)
    private AnalysisJob job;

    @Column(name = "target_source_id", nullable = false, updatable = false)
    private Long targetSourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumns({
        @JoinColumn(name = "job_id", referencedColumnName = "job_id", insertable = false, updatable = false),
        @JoinColumn(name = "target_source_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
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
        this.targetSourceId = targetSource.getId();
        this.type = type;
        this.quote = quote;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.reason = reason;
        this.suggestion = suggestion;
    }
}
