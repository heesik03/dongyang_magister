package com.magister.backend.domain.setting.image;

import com.magister.backend.domain.setting.entity.WorkSetting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/** JDBC 워커가 선점하는 인물 이미지 작업의 스키마를 정의한다. */
@Entity
@Table(
        name = "character_image_jobs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"setting_id", "request_id"}),
        indexes = {
            @Index(name = "ix_character_image_dispatch", columnList = "status,created_at,id"),
            @Index(name = "ix_character_image_history", columnList = "setting_id,created_at,id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterImageJob {
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "setting_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private WorkSetting setting;

    @Column(nullable = false)
    private long settingVersion;

    @Column(nullable = false, length = 64)
    private String requestId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "is_primary", nullable = false)
    @ColumnDefault("false")
    private boolean primary;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant leaseUntil;

    @Column(length = 32)
    private String mimeType;

    @Column(columnDefinition = "MEDIUMBLOB")
    private byte[] imageBytes;
}
