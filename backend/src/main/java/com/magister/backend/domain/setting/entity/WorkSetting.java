package com.magister.backend.domain.setting.entity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "work_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_id", nullable = false, updatable = false)
    private Long workId;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SettingKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SettingStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private Long version;

    public WorkSetting(
            Long workId, String title, SettingKind kind, SettingStatus status, String content) {
        this.workId = workId;
        update(title, kind, status, content);
    }

    public void update(String title, SettingKind kind, SettingStatus status, String content) {
        this.title = title.strip();
        this.kind = kind;
        this.status = status;
        this.content = content;
    }
}
