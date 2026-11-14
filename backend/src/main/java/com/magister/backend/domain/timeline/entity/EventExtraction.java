package com.magister.backend.domain.timeline.entity;

import com.magister.backend.domain.work.entity.Work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/** 원고 스냅샷·후보 편집·외부 호출 상태를 새로고침과 재시작 뒤에도 보존한다. */
@Entity
@Table(
        name = "event_extractions",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_extraction_request", columnNames = {"work_id", "request_id"})
        },
        indexes = {
            @Index(name = "ix_extraction_history", columnList = "work_id,created_at,id"),
            @Index(name = "ix_extraction_work_status", columnList = "work_id,status"),
            @Index(name = "ix_extraction_active", columnList = "status,lease_until")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventExtraction {
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "work_id")
    private Work work;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "request_body", nullable = false, columnDefinition = "LONGTEXT")
    private String requestBody;

    @Column(name = "input_json", nullable = false, columnDefinition = "LONGTEXT")
    private String input;

    @Column(name = "result_json", nullable = false, columnDefinition = "LONGTEXT")
    private String result;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", length = 500)
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "lease_until", nullable = false)
    private Instant leaseUntil;

    @Version private Long version;

    public EventExtraction(
            String id, Work work, String requestId, String requestBody, String input) {
        this.id = id;
        this.work = work;
        this.requestId = requestId;
        this.requestBody = requestBody;
        this.input = input;
        result = "[]";
        status = "QUEUED";
        createdAt = Instant.now();
        leaseUntil = createdAt.plusSeconds(300);
    }

    public boolean active() {
        return status.equals("QUEUED") || status.equals("RUNNING");
    }

    public boolean start() {
        if (!status.equals("QUEUED")) {
            return false;
        }
        status = "RUNNING";
        leaseUntil = Instant.now().plusSeconds(120);
        return true;
    }

    public void succeed(String result) {
        if (status.equals("RUNNING")) {
            this.result = result;
            status = "SUCCEEDED";
        }
    }

    public void fail(String error) {
        if (active()) {
            status = "FAILED";
            this.error = error;
        }
    }

    public void cancel() {
        if (active()) {
            status = "CANCELLED";
        }
    }

    public void edit(String result) {
        this.result = result;
    }
}
