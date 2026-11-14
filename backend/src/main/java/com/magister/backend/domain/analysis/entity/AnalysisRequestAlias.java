package com.magister.backend.domain.analysis.entity;

import com.magister.backend.domain.work.entity.Work;

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

/** JDBC로 저장하는 분석 접수 별칭의 스키마를 정의한다. */
@Entity
@Table(name = "analysis_request_aliases")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisRequestAlias {
    @EmbeddedId
    private Key id;

    @MapsId("workId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Work work;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AnalysisJob job;

    @Column(name = "request_body", nullable = false, columnDefinition = "LONGTEXT")
    private String requestBody;

    @Embeddable
    public record Key(
            @Column(name = "work_id") Long workId,
            @Column(name = "request_id", length = 64) String requestId)
            implements Serializable {}
}
