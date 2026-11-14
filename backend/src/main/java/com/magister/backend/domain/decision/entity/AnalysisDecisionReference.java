package com.magister.backend.domain.decision.entity;

import com.magister.backend.domain.analysis.entity.AnalysisJob;

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

/** 분석에 전송된 작가 판단 범위를 JDBC로 저장한다. */
@Entity
@Table(name = "analysis_decision_references")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisDecisionReference {
    @EmbeddedId
    private Key id;

    @MapsId("jobId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AnalysisJob job;

    @MapsId("scopeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scope_id", nullable = false)
    private ReviewDecisionScope scope;

    @Column(name = "usage_type", nullable = false, length = 20)
    private String usageType;

    @Embeddable
    public record Key(
            @Column(name = "job_id", length = 36) String jobId,
            @Column(name = "scope_id") Long scopeId)
            implements Serializable {}
}
