package com.magister.backend.domain.analysis.entity;

import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.work.entity.Work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "analysis_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisJob {
    private static final Duration INITIAL_LEASE = Duration.ofMinutes(2);

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_chapter_id", updatable = false)
    private Chapter targetChapter;

    @Column(name = "chapter_key_snapshot", length = 64, updatable = false)
    private String chapterKey;

    @Column(name = "chapter_title_snapshot", length = 100, updatable = false)
    private String chapterTitle;

    @Column(name = "policy_version", length = 50, updatable = false)
    private String policyVersion;

    @Column(name = "provider_model", length = 200, updatable = false)
    private String providerModel;

    @Column(name = "source_format_version", nullable = false, updatable = false)
    private int sourceFormatVersion;

    public void recordConfirmation(Chapter chapter, String policyVersion, String model) {
        targetChapter = chapter;
        chapterKey = chapter.getChapterKey();
        chapterTitle = chapter.getTitle();
        this.policyVersion = policyVersion;
        this.providerModel = model;
        sourceFormatVersion = 1;
    }

    public AnalysisJob(String id, Work work, String requestId, String requestBody, String input) {
        this.id = id;
        this.work = work;
        this.requestId = requestId;
        this.requestBody = requestBody;
        this.input = input;
        status = AnalysisJobStatus.QUEUED;
        createdAt = Instant.now();
        updatedAt = createdAt;
        leaseUntil = createdAt.plus(INITIAL_LEASE);
    }

    /** 실행 소유자와 만료 시각은 접수 시 한 번 배정하고 생존 작업만 갱신한다. */
    public void assignLease(String workerId, Instant leaseUntil) {
        this.workerId = workerId;
        this.leaseUntil = leaseUntil;
    }

    /** 중복 전달된 RUNNING 작업은 다시 선점하지 않는다. */
    public boolean start() {
        if (status != AnalysisJobStatus.QUEUED) {
            return false;
        }
        status = AnalysisJobStatus.RUNNING;
        updatedAt = Instant.now();
        return true;
    }

    /** 취소·실패가 먼저 확정되었으면 늦게 도착한 공급자 결과를 적용하지 않는다. */
    public void succeed(String result) {
        if (status != AnalysisJobStatus.RUNNING) {
            return;
        }
        status = AnalysisJobStatus.SUCCEEDED;
        this.result = result;
        updatedAt = Instant.now();
    }

    /** 선점 전 오류도 실패 처리하되 완료·취소 결과는 보존한다. */
    public void fail(String error) {
        if (terminal()) {
            return;
        }
        status = AnalysisJobStatus.FAILED;
        this.error = error;
        updatedAt = Instant.now();
    }

    /** 여러 번 취소해도 종료 상태와 종료 시각을 다시 변경하지 않는다. */
    public void cancel() {
        if (terminal()) {
            return;
        }
        status = AnalysisJobStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    /** 종료 상태는 워커의 늦은 결과·오류에 의해 다시 바뀌지 않는다. */
    public boolean terminal() {
        return status.terminal();
    }
}
