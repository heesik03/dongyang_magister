package com.magister.backend.domain.work.repository;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 작품 잠금 아래에서 FK 의존 순서를 지켜 삭제한다. 제약을 비활성화하지 않는다. */
@Repository
@RequiredArgsConstructor
public class WorkDeletionRepository {
    private final JdbcTemplate jdbcTemplate;

    public record LockedJob(String id, String status) {}

    /** 완료 저장·정리와 같은 작업 행을 ID 순서로 잠그며 본문을 읽지 않는다. */
    public List<LockedJob> lockJobs(Long workId) {
        return jdbcTemplate.query(
                "SELECT id,status FROM analysis_jobs WHERE work_id=? ORDER BY id FOR UPDATE",
                (row, index) -> new LockedJob(row.getString("id"), row.getString("status")),
                workId);
    }

    /** 실행 중 작업을 먼저 종료시키고 참조 -> 판단 -> 결과 -> 원본 순서로 같은 트랜잭션에서 제거한다. */
    public void delete(Long workId) {
        jdbcTemplate.update(
                "UPDATE analysis_jobs SET"
                    + " status='CANCELLED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE"
                    + " work_id=? AND status IN ('QUEUED','RUNNING')",
                workId);
        jdbcTemplate.update(
                "DELETE FROM analysis_decision_references WHERE job_id IN (SELECT id FROM"
                    + " analysis_jobs WHERE work_id=?)",
                workId);
        jdbcTemplate.update(
                "DELETE FROM review_decision_scopes WHERE decision_id IN (SELECT d.id FROM"
                    + " review_decisions d JOIN review_candidates r ON r.id=d.review_id JOIN"
                    + " analysis_jobs j ON j.id=r.job_id WHERE j.work_id=?)",
                workId);
        jdbcTemplate.update(
                "DELETE FROM review_decisions WHERE review_id IN (SELECT r.id FROM"
                    + " review_candidates r JOIN analysis_jobs j ON j.id=r.job_id WHERE"
                    + " j.work_id=?)",
                workId);
        jdbcTemplate.update(
                "DELETE FROM review_evidences WHERE job_id IN (SELECT id FROM analysis_jobs WHERE"
                    + " work_id=?)",
                workId);
        jdbcTemplate.update(
                "DELETE FROM review_candidates WHERE job_id IN (SELECT id FROM analysis_jobs WHERE"
                    + " work_id=?)",
                workId);
        jdbcTemplate.update(
                "DELETE FROM analysis_sources WHERE job_id IN (SELECT id FROM analysis_jobs WHERE"
                    + " work_id=?)",
                workId);
        jdbcTemplate.update("DELETE FROM analysis_jobs WHERE work_id=?", workId);
        jdbcTemplate.update("DELETE FROM workspaces WHERE work_id=?", workId);
        jdbcTemplate.update("DELETE FROM chapters WHERE work_id=?", workId);
        jdbcTemplate.update("DELETE FROM work_settings WHERE work_id=?", workId);
        jdbcTemplate.update("DELETE FROM works WHERE id=?", workId);
    }
}
