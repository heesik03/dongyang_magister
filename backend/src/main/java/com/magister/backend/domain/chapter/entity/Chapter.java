package com.magister.backend.domain.chapter.entity;

import com.magister.backend.domain.work.entity.Work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "chapters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chapter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false, updatable = false)
    private Work work;

    // 기존 JSON의 회차 ID는 작품 안에서만 유일하므로 물리 PK와 구분한다.
    @Column(name = "chapter_key", nullable = false, length = 64, updatable = false)
    private String chapterKey;

    @Column(name = "chapter_no", nullable = false, updatable = false)
    private int chapterNo;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(nullable = false)
    private long revision;

    @Version private Long version;

    @Column(name = "mutation_id", nullable = false, length = 64)
    private String mutationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Chapter(
            Work work,
            String key,
            int number,
            String title,
            String content,
            long revision,
            String mutationId) {
        this.work = work;
        this.chapterKey = key;
        this.chapterNo = number;
        this.title = title;
        this.content = content;
        this.revision = revision;
        this.mutationId = mutationId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String title, String content, String mutationId) {
        this.title = title;
        this.content = content;
        this.mutationId = mutationId;
        revision++;
        updatedAt = Instant.now();
    }
}
