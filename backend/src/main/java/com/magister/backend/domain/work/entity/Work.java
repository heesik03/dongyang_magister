package com.magister.backend.domain.work.entity;

import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.user.entity.User;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "works")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Work {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "owner_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_works_owner"))
    private User owner;

    @OneToMany(mappedBy = "work", fetch = FetchType.LAZY)
    private List<WorkSetting> settings = new ArrayList<>();

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

    public Work(User owner, String title, String genre, String era, String description) {
        assignOwner(owner);
        update(title, genre, era, description);
    }

    public void assignOwner(User owner) {
        // 소유권 이전은 지원하지 않으며, 생성 시 양쪽 객체의 관계를 함께 맞춘다.
        if (this.owner != null && this.owner != owner) {
            throw new IllegalStateException("작품 소유자는 변경할 수 없습니다.");
        }
        this.owner = java.util.Objects.requireNonNull(owner);
        if (!owner.getWorks().contains(this)) {
            owner.getWorks().add(this);
        }
    }

    public void addSetting(WorkSetting setting) {
        setting.assignWork(this);
    }

    public void update(String title, String genre, String era, String description) {
        this.title = title.strip();
        this.genre = genre.strip();
        this.era = era.strip();
        this.description = description.strip();
    }
}
