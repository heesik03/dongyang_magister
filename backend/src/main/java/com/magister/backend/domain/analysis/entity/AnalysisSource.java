package com.magister.backend.domain.analysis.entity;

import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.setting.entity.WorkSetting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** 접수 시 한 번 저장하며 현재 원고·설정 수정으로 내용을 바꾸지 않는 근거 자료다. */
@Entity
@Table(
        name = "analysis_sources",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_source_identity", columnNames = {"job_id", "source_type", "source_key"}),
            @UniqueConstraint(name = "uk_source_job_id", columnNames = {"id", "job_id"})
        })
@Check(constraints = """
        (source_type = 'SETTING'
            AND chapter_id IS NULL)
        OR (source_type IN ('TARGET','PREVIOUS')
            AND chapter_id IS NOT NULL)
        """)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "job_id", nullable = false, updatable = false)
    private AnalysisJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", updatable = false)
    private Chapter chapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "setting_id", updatable = false)
    private WorkSetting setting;

    @Column(name = "source_type", nullable = false, length = 20, updatable = false)
    private String type;

    @Column(name = "source_key", nullable = false, length = 64, updatable = false)
    private String sourceKey;

    @Column(name = "source_revision", nullable = false, updatable = false)
    private long revision;

    @Column(name = "title_snapshot", nullable = false, length = 100, updatable = false)
    private String title;

    @Column(
            name = "content_snapshot",
            nullable = false,
            columnDefinition = "LONGTEXT",
            updatable = false)
    private String content;

    @Column(
            name = "metadata_snapshot",
            nullable = false,
            columnDefinition = "LONGTEXT",
            updatable = false)
    private String metadata;

    @Column(name = "content_hash", nullable = false, length = 64, updatable = false)
    private String contentHash;

    public AnalysisSource(
            AnalysisJob job,
            Chapter chapter,
            WorkSetting setting,
            String type,
            String sourceKey,
            long revision,
            String title,
            String content,
            String metadata,
            String contentHash) {
        this.job = job;
        this.chapter = chapter;
        this.setting = setting;
        this.type = type;
        this.sourceKey = sourceKey;
        this.revision = revision;
        this.title = title;
        this.content = content;
        this.metadata = metadata;
        this.contentHash = contentHash;
    }
}
