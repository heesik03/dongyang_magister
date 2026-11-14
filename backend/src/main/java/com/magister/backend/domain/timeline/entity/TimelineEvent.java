package com.magister.backend.domain.timeline.entity;

import com.magister.backend.domain.work.entity.Work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(
        name = "timeline_events",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_timeline_events_key", columnNames = {"work_id", "event_key"})
        },
        indexes = {
            @Index(name = "ix_timeline_events_work_seq", columnList = "work_id,sequence,id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimelineEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "work_id", nullable = false, updatable = false)
    private Work work;

    @Column(name = "event_key", nullable = false, length = 64, updatable = false)
    private String eventKey;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "time_text", nullable = false, length = 100)
    private String time;

    @Column(name = "characters_text", nullable = false, length = 500)
    private String characters;

    @Column(name = "chapter_id", nullable = false, length = 64)
    private String chapterId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String quote;

    @Column
    private Integer sequence;

    @Column(name = "time_status", length = 20)
    private String timeStatus;

    @Column(name = "source_revision")
    private Long sourceRevision;

    @Column(name = "start_offset")
    private Integer startOffset;

    @Column(name = "end_offset")
    private Integer endOffset;

    @Column(name = "extraction_id", length = 36)
    private String extractionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TimelineEvent(
            Work work,
            String eventKey,
            String title,
            String description,
            String time,
            String characters,
            String chapterId,
            String quote,
            Integer sequence,
            String timeStatus,
            Long sourceRevision,
            Integer startOffset,
            Integer endOffset,
            String extractionId) {
        this.work = work;
        this.eventKey = eventKey;
        this.title = title;
        this.description = description;
        this.time = time;
        this.characters = characters;
        this.chapterId = chapterId;
        this.quote = quote;
        this.sequence = sequence;
        this.timeStatus = timeStatus;
        this.sourceRevision = sourceRevision;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.extractionId = extractionId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(
            String title,
            String description,
            String time,
            String characters,
            String chapterId,
            String quote,
            Integer sequence,
            String timeStatus,
            Long sourceRevision,
            Integer startOffset,
            Integer endOffset,
            String extractionId) {
        this.title = title;
        this.description = description;
        this.time = time;
        this.characters = characters;
        this.chapterId = chapterId;
        this.quote = quote;
        this.sequence = sequence;
        this.timeStatus = timeStatus;
        this.sourceRevision = sourceRevision;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.extractionId = extractionId;
        this.updatedAt = Instant.now();
    }
}
