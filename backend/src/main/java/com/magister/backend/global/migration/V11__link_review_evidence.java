package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/** 기존 JSON에 없는 정확한 위치는 이관하지 않고 결과 형식 0으로 보존한다. */
public class V11__link_review_evidence extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute(
                    "ALTER TABLE analysis_jobs ADD result_format_version INT NOT NULL DEFAULT 0");
            statement.execute(
                    "ALTER TABLE analysis_sources ADD CONSTRAINT uk_source_job_id"
                        + " UNIQUE(job_id,id)");
            statement.execute(
                    """
                    CREATE TABLE review_candidates (
                      id VARCHAR(36) NOT NULL PRIMARY KEY,
                      job_id VARCHAR(36) NOT NULL,
                      target_source_id BIGINT NOT NULL,
                      type VARCHAR(20) NOT NULL,
                      quote LONGTEXT NOT NULL,
                      start_offset INT NOT NULL,
                      end_offset INT NOT NULL,
                      reason LONGTEXT NOT NULL,
                      suggestion LONGTEXT NOT NULL,
                      CONSTRAINT uk_review_job_id UNIQUE(job_id,id),
                      CONSTRAINT fk_review_job FOREIGN KEY(job_id) REFERENCES analysis_jobs(id) ON DELETE CASCADE,
                      CONSTRAINT fk_review_target FOREIGN KEY(job_id,target_source_id) REFERENCES analysis_sources(job_id,id) ON DELETE CASCADE,
                      CONSTRAINT ck_review_type CHECK(type IN ('설정 충돌 후보','설명 보완')),
                      CONSTRAINT ck_review_range CHECK(start_offset>=0 AND end_offset>start_offset)
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE review_evidences (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      job_id VARCHAR(36) NOT NULL,
                      review_id VARCHAR(36) NOT NULL,
                      source_id BIGINT NOT NULL,
                      quote LONGTEXT NOT NULL,
                      start_offset INT NOT NULL,
                      end_offset INT NOT NULL,
                      explanation LONGTEXT NOT NULL,
                      CONSTRAINT fk_evidence_review FOREIGN KEY(job_id,review_id) REFERENCES review_candidates(job_id,id) ON DELETE CASCADE,
                      CONSTRAINT fk_evidence_source FOREIGN KEY(job_id,source_id) REFERENCES analysis_sources(job_id,id) ON DELETE CASCADE,
                      CONSTRAINT ck_evidence_range CHECK(start_offset>=0 AND end_offset>start_offset)
                    )
                    """);
            statement.execute("CREATE INDEX ix_evidence_review ON review_evidences(review_id,id)");
        }
    }
}
