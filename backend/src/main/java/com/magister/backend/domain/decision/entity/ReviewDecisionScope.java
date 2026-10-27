package com.magister.backend.domain.decision.entity;

import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.chapter.entity.Chapter;

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

/** 판단 버전과 그 적용 범위는 추가만 허용하고 과거 값을 수정하지 않는다. */
@Entity
@Table(name = "review_decision_scopes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewDecisionScope {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false, updatable = false)
    private ReviewDecision decision;

    @Column(nullable = false, length = 20, updatable = false)
    private String scopeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_chapter_id", updatable = false)
    private Chapter fromChapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_chapter_id", updatable = false)
    private Chapter toChapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_source_id", updatable = false)
    private AnalysisSource sceneSource;

    @Column(updatable = false)
    private Integer startOffset;

    @Column(updatable = false)
    private Integer endOffset;

    @Column(nullable = false, length = 2000, updatable = false)
    private String conditionText;

    public ReviewDecisionScope(
            ReviewDecision decision,
            String scopeType,
            Chapter fromChapter,
            Chapter toChapter,
            AnalysisSource sceneSource,
            Integer startOffset,
            Integer endOffset,
            String conditionText) {
        this.decision = decision;
        this.scopeType = scopeType;
        this.fromChapter = fromChapter;
        this.toChapter = toChapter;
        this.sceneSource = sceneSource;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.conditionText = conditionText;
    }
}
