package com.magister.backend.domain.analysis.repository;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class WorkReviewOverviewRepository {
    private final JdbcTemplate jdbcTemplate;

    public record ChapterCounts(
            String chapterId, int chapterNo, String title, long currentRevision,
            String jobId, Long analyzedRevision, Instant analyzedAt,
            int settingCount, int previousChapterCount, int analysisRunCount,
            int totalCandidateCount, int conflictCount, int supplementCount,
            int unreviewedCount, int acceptedCount, int holdCount, int intendedCount) {}

    /** 회차별 최신 성공 분석의 메타데이터와 최신 판단 건수만 한 질의로 집계한다. */
    public List<ChapterCounts> findChapterCounts(Long workId) {
        return jdbcTemplate.query("""
                WITH ranked_jobs AS (
                    SELECT id, target_chapter_id, updated_at,
                           ROW_NUMBER() OVER (PARTITION BY target_chapter_id
                                              ORDER BY created_at DESC, id DESC) AS row_no,
                           COUNT(*) OVER (PARTITION BY target_chapter_id) AS run_count
                    FROM analysis_jobs
                    WHERE work_id = ? AND status = 'SUCCEEDED' AND target_chapter_id IS NOT NULL
                ), latest_jobs AS (
                    SELECT id, target_chapter_id, updated_at, run_count
                    FROM ranked_jobs WHERE row_no = 1
                ), source_counts AS (
                    SELECT source.job_id,
                           MAX(CASE WHEN source.source_type = 'TARGET' THEN source.source_revision END) AS analyzed_revision,
                           SUM(CASE WHEN source.source_type = 'SETTING' THEN 1 ELSE 0 END) AS setting_count,
                           SUM(CASE WHEN source.source_type = 'PREVIOUS' THEN 1 ELSE 0 END) AS previous_count
                    FROM analysis_sources source JOIN latest_jobs job ON job.id = source.job_id
                    GROUP BY source.job_id
                ), review_counts AS (
                    SELECT candidate.job_id, COUNT(*) AS total_count,
                           SUM(CASE WHEN candidate.type = '설정 충돌 후보' THEN 1 ELSE 0 END) AS conflict_count,
                           SUM(CASE WHEN candidate.type = '설명 보완' THEN 1 ELSE 0 END) AS supplement_count,
                           SUM(CASE WHEN decision.status IS NULL OR decision.status = '미검토' THEN 1 ELSE 0 END) AS unreviewed_count,
                           SUM(CASE WHEN decision.status = '수용' THEN 1 ELSE 0 END) AS accepted_count,
                           SUM(CASE WHEN decision.status = '보류' THEN 1 ELSE 0 END) AS hold_count,
                           SUM(CASE WHEN decision.status = '의도된 설정' THEN 1 ELSE 0 END) AS intended_count
                    FROM review_candidates candidate JOIN latest_jobs job ON job.id = candidate.job_id
                    LEFT JOIN review_decisions decision ON decision.review_id = candidate.id
                      AND decision.decision_version = (
                        SELECT MAX(history.decision_version) FROM review_decisions history
                        WHERE history.review_id = candidate.id
                      )
                    GROUP BY candidate.job_id
                )
                SELECT chapter.chapter_key, chapter.chapter_no, chapter.title, chapter.revision,
                       job.id AS job_id, job.updated_at, job.run_count,
                       sources.analyzed_revision, sources.setting_count, sources.previous_count,
                       reviews.total_count, reviews.conflict_count, reviews.supplement_count,
                       reviews.unreviewed_count, reviews.accepted_count, reviews.hold_count, reviews.intended_count
                FROM chapters chapter
                LEFT JOIN latest_jobs job ON job.target_chapter_id = chapter.id
                LEFT JOIN source_counts sources ON sources.job_id = job.id
                LEFT JOIN review_counts reviews ON reviews.job_id = job.id
                WHERE chapter.work_id = ? ORDER BY chapter.chapter_no, chapter.id
                """, (row, index) -> {
            Timestamp analyzedAt = row.getTimestamp("updated_at");
            long analyzedRevision = row.getLong("analyzed_revision");
            Long revision = row.wasNull() ? null : analyzedRevision;
            return new ChapterCounts(
                    row.getString("chapter_key"), row.getInt("chapter_no"), row.getString("title"),
                    row.getLong("revision"), row.getString("job_id"), revision,
                    analyzedAt == null ? null : analyzedAt.toInstant(),
                    row.getInt("setting_count"), row.getInt("previous_count"), row.getInt("run_count"),
                    row.getInt("total_count"), row.getInt("conflict_count"), row.getInt("supplement_count"),
                    row.getInt("unreviewed_count"), row.getInt("accepted_count"), row.getInt("hold_count"),
                    row.getInt("intended_count"));
        }, workId, workId);
    }
}
