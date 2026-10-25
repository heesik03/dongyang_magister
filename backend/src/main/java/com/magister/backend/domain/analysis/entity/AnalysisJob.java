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

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String result;

    @Column(name = "error_message", length = 500)
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private Long version;

    public AnalysisJob(String id, Work work, String requestId, String requestBody, String input) {
        this.id = id;
        this.work = work;
        this.requestId = requestId;
        this.requestBody = requestBody;
        this.input = input;
        status = "QUEUED";
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    public void transition(String status, String result, String error) {
        this.status = status;
        this.result = result;
        this.error = error;
        updatedAt = Instant.now();
    }

    public boolean terminal() {
        return status.equals("SUCCEEDED") || status.equals("FAILED") || status.equals("CANCELLED");
    }
}
