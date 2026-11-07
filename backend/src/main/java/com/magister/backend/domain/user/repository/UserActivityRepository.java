package com.magister.backend.domain.user.repository;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 마이페이지 창작 활동 집계를 원고·분석 본문(LONGTEXT) 로딩 없이 조회한다. */
@Repository
@RequiredArgsConstructor
public class UserActivityRepository {
    private final JdbcTemplate jdbcTemplate;

    public record RecentManuscriptRow(
            Long workId,
            String workTitle,
            String chapterId,
            int chapterNo,
            String chapterTitle,
            Instant updatedAt) {}

    /** 본인 소유 작품 수를 집계한다. */
    public long countOwnedWorks(Long ownerId) {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM works WHERE owner_id = ?", Long.class, ownerId);
        return count == null ? 0L : count;
    }

    /** 본인 소유 작품에 저장된 전체 회차 수를 집계한다. */
    public long countOwnedChapters(Long ownerId) {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM chapters c JOIN works w ON w.id = c.work_id WHERE"
                                + " w.owner_id = ?",
                        Long.class,
                        ownerId);
        return count == null ? 0L : count;
    }

    /**
     * 각 회차의 현재 원고 버전(revision)에 대해 가장 최근에 성공(SUCCEEDED)한 분석 1건만 기준으로 삼아 아직 작가 판단이 완료되지 않은(미검토) 후보 수를
     * 집계한다. 이전 원고 버전의 결과나 동일 회차의 중복 재분석 이력은 합산하지 않는다.
     */
    public long countUnreviewedLatestCandidates(Long ownerId) {
        Long count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(r.id)
                        FROM review_candidates r
                        JOIN analysis_jobs j ON j.id = r.job_id
                        JOIN analysis_sources ts ON ts.job_id = r.job_id AND ts.id = r.target_source_id
                        JOIN chapters c ON c.id = j.target_chapter_id
                        JOIN works w ON w.id = c.work_id
                        WHERE w.owner_id = ?
                          AND j.status = 'SUCCEEDED'
                          AND ts.source_type = 'TARGET'
                          AND ts.source_revision = c.revision
                          AND NOT EXISTS (
                              SELECT 1 FROM analysis_jobs newer
                              WHERE newer.target_chapter_id = c.id
                                AND newer.status = 'SUCCEEDED'
                                AND (newer.created_at > j.created_at
                                     OR (newer.created_at = j.created_at AND newer.id > j.id))
                          )
                          AND NOT EXISTS (
                              SELECT 1 FROM review_decisions d
                              WHERE d.review_id = r.id
                                AND d.decision_version = (
                                    SELECT MAX(d2.decision_version)
                                    FROM review_decisions d2
                                    WHERE d2.review_id = r.id
                                )
                                AND d.status <> '미검토'
                          )
                        """,
                        Long.class,
                        ownerId);
        return count == null ? 0L : count;
    }

    /** 본인 작품 가운데 가장 최근에 수정·저장된 회차 요약 1건을 조회한다. */
    public Optional<RecentManuscriptRow> findRecentManuscript(Long ownerId) {
        List<RecentManuscriptRow> rows =
                jdbcTemplate.query(
                        """
                        SELECT w.id AS work_id,
                               w.title AS work_title,
                               c.chapter_key,
                               c.chapter_no,
                               c.title AS chapter_title,
                               c.updated_at
                        FROM chapters c
                        JOIN works w ON w.id = c.work_id
                        WHERE w.owner_id = ?
                        ORDER BY c.updated_at DESC, c.id DESC
                        LIMIT 1
                        """,
                        (row, index) -> {
                            Timestamp timestamp = row.getTimestamp("updated_at");
                            Instant updatedAt =
                                    timestamp == null ? Instant.EPOCH : timestamp.toInstant();
                            return new RecentManuscriptRow(
                                    row.getLong("work_id"),
                                    row.getString("work_title"),
                                    row.getString("chapter_key"),
                                    row.getInt("chapter_no"),
                                    row.getString("chapter_title"),
                                    updatedAt);
                        },
                        ownerId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }
}
