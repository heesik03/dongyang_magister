package com.magister.backend.domain.analysis.entity;

import com.magister.backend.domain.work.entity.Work;

import jakarta.persistence.*;

import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "analysis_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisJob {
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id")
    private Work work;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "request_body", nullable = false, columnDefinition = "LONGTEXT")
    private String requestBody;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String input;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisJobStatus status;

    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String result;

    @Column(name = "error_message", length = 500)
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private Long version;

    @Column(name = "worker_id", length = 36)
    private String workerId;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    public AnalysisJob(String id, Work work, String requestId, String requestBody, String input) {
        this.id = id;
        this.work = work;
        this.requestId = requestId;
        this.requestBody = requestBody;
        this.input = input;
        status = AnalysisJobStatus.QUEUED;
        createdAt = Instant.now();
        updatedAt = createdAt;
        leaseUntil = createdAt.plusSeconds(120);
    }

    /** 실행 소유자와 만료 시각은 접수 시 한 번 배정하고 생존 작업만 갱신한다. */
    public void assignLease(String workerId, Instant leaseUntil) {
        this.workerId = workerId;
        this.leaseUntil = leaseUntil;
    }

    /** 중복 전달된 RUNNING 작업은 다시 선점하지 않는다. */
    public boolean start() {
        if (status != AnalysisJobStatus.QUEUED) return false;
        status = AnalysisJobStatus.RUNNING;
        updatedAt = Instant.now();
        return true;
    }

    /** 취소·실패가 먼저 확정되었으면 늦게 도착한 공급자 결과를 적용하지 않는다. */
    public void succeed(String result) {
        if (status != AnalysisJobStatus.RUNNING) return;
        status = AnalysisJobStatus.SUCCEEDED;
        this.result = result;
        updatedAt = Instant.now();
    }

    public void fail(String error) {
        if (terminal()) return;
        status = AnalysisJobStatus.FAILED;
        this.error = error;
        updatedAt = Instant.now();
    }

    public void cancel() {
        if (terminal()) return;
        status = AnalysisJobStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    public boolean terminal() {
        return status.terminal();
    }
}
