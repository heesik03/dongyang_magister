package com.magister.backend.domain.workspace.entity;

import com.magister.backend.domain.work.entity.Work;

import jakarta.persistence.*;

import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "workspaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace {
    @Id
    @Column(name = "work_id")
    private Long workId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id")
    private Work work;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(nullable = false)
    private long revision;

    @Column(name = "mutation_id", length = 64)
    private String mutationId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private Long version;

    public Workspace(Work work) {
        this.work = work;
        this.workId = work.getId();
    }

    public void replace(String content, String mutationId) {
        this.content = content;
        this.mutationId = mutationId;
        revision++;
        updatedAt = Instant.now();
    }
}
