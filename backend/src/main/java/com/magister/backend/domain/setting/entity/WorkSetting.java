package com.magister.backend.domain.setting.entity;

import com.magister.backend.domain.work.entity.Work;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "work_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "work_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_settings_work"))
    private Work work;

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
            Work work, String title, SettingKind kind, SettingStatus status, String content) {
        assignWork(work);
        update(title, kind, status, content);
    }

    public void assignWork(Work work) {
        Objects.requireNonNull(work);
        // 같은 영속 엔티티의 프록시/실체를 재전달해도 관계를 중복 연결하지 않는다.
        if (this.work != null) {
            if (this.work == work
                    || (this.work.getId() != null && this.work.getId().equals(work.getId())))
                return;
            throw new IllegalStateException("설정이 속한 작품은 변경할 수 없습니다.");
        }
        this.work = work;
        // contains()는 LAZY 컬렉션 전체를 읽는다. 최초 연결 한 번만 수행해 중복을 방지한다.
        work.getSettings().add(this);
    }

    public void update(String title, SettingKind kind, SettingStatus status, String content) {
        this.title = title.strip();
        this.kind = kind;
        this.status = status;
        this.content = content;
    }
}
