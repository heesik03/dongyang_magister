package com.magister.backend.domain.work.entity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "works")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Work {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소유자 변경 API는 제공하지 않으며 FK는 마이그레이션에서 보장한다.
    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 30)
    private String genre;

    @Column(nullable = false, length = 100)
    private String era;

    @Column(nullable = false, length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private Long version;

    public Work(Long ownerId, String title, String genre, String era, String description) {
        this.ownerId = ownerId;
        update(title, genre, era, description);
    }

    public void update(String title, String genre, String era, String description) {
        this.title = title.strip();
        this.genre = genre.strip();
        this.era = era.strip();
        this.description = description.strip();
    }
}
