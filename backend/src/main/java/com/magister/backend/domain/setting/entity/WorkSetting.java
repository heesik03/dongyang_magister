package com.magister.backend.domain.setting.entity;

import com.magister.backend.domain.work.entity.Work;

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
        // 설정을 다른 작품으로 옮길 수 없도록 하고 역방향 컬렉션도 함께 유지한다.
        if (this.work != null && this.work != work) {
            throw new IllegalStateException("설정이 속한 작품은 변경할 수 없습니다.");
        }
        this.work = java.util.Objects.requireNonNull(work);
        if (!work.getSettings().contains(this)) {
            work.getSettings().add(this);
        }
    }

    public void update(String title, SettingKind kind, SettingStatus status, String content) {
        this.title = title.strip();
        this.kind = kind;
        this.status = status;
        this.content = content;
    }
}
