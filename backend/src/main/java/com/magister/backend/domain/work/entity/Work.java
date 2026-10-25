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
import java.util.Objects;

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
        Objects.requireNonNull(owner);
        // 같은 영속 엔티티의 프록시/실체를 재전달해도 관계를 중복 연결하지 않는다.
        if (this.owner != null) {
            if (this.owner == owner
                    || (this.owner.getId() != null && this.owner.getId().equals(owner.getId())))
                return;
            throw new IllegalStateException("작품 소유자는 변경할 수 없습니다.");
        }
        this.owner = owner;
        // contains()는 LAZY 컬렉션 전체를 읽는다. 최초 연결 한 번만 수행해 중복을 방지한다.
        owner.getWorks().add(this);
    }

    public void addSetting(WorkSetting setting) {
        setting.assignWork(this);
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    public void update(String title, String genre, String era, String description) {
        this.title = title.strip();
        this.genre = genre.strip();
        this.era = era.strip();
        this.description = description.strip();
    }
}
