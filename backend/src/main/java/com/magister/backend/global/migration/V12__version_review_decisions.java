package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/** 범위 없는 과거 workspace 판단은 원본에 보존하고 임의의 회차 범위를 만들지 않는다. */
public class V12__version_review_decisions extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE review_decisions (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      review_id VARCHAR(36) NOT NULL,
                      decision_version BIGINT NOT NULL,
                      status VARCHAR(20) NOT NULL,
                      reason LONGTEXT NOT NULL,
                      mutation_id VARCHAR(64) NOT NULL,
                      request_body LONGTEXT NOT NULL,
                      basis_hash VARCHAR(64) NOT NULL,
                      created_at TIMESTAMP(6) NOT NULL,
                      CONSTRAINT fk_decision_review FOREIGN KEY(review_id) REFERENCES review_candidates(id) ON DELETE CASCADE,
                      CONSTRAINT uk_decision_version UNIQUE(review_id,decision_version),
                      CONSTRAINT uk_decision_mutation UNIQUE(review_id,mutation_id),
                      CONSTRAINT ck_decision_version CHECK(decision_version>0),
                      CONSTRAINT ck_decision_status CHECK(status IN ('미검토','수용','보류','의도된 설정'))
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE review_decision_scopes (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      decision_id BIGINT NOT NULL,
                      scope_type VARCHAR(20) NOT NULL,
                      from_chapter_id BIGINT,
                      to_chapter_id BIGINT,
                      scene_source_id BIGINT,
                      start_offset INT,
                      end_offset INT,
                      condition_text VARCHAR(2000) NOT NULL,
                      CONSTRAINT fk_scope_decision FOREIGN KEY(decision_id) REFERENCES review_decisions(id) ON DELETE CASCADE,
                      CONSTRAINT fk_scope_from FOREIGN KEY(from_chapter_id) REFERENCES chapters(id),
                      CONSTRAINT fk_scope_to FOREIGN KEY(to_chapter_id) REFERENCES chapters(id),
                      CONSTRAINT fk_scope_scene FOREIGN KEY(scene_source_id) REFERENCES analysis_sources(id),
                      CONSTRAINT ck_scope_fields CHECK(
                        (scope_type='CHAPTER_RANGE' AND from_chapter_id IS NOT NULL AND scene_source_id IS NULL AND start_offset IS NULL AND end_offset IS NULL)
                        OR (scope_type='SCENE' AND from_chapter_id IS NULL AND to_chapter_id IS NULL AND scene_source_id IS NOT NULL AND start_offset>=0 AND end_offset>start_offset AND start_offset IS NOT NULL AND end_offset IS NOT NULL))
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE analysis_decision_references (
                      job_id VARCHAR(36) NOT NULL,
                      scope_id BIGINT NOT NULL,
                      usage_type VARCHAR(20) NOT NULL,
                      PRIMARY KEY(job_id,scope_id),
                      CONSTRAINT fk_decision_ref_job FOREIGN KEY(job_id) REFERENCES analysis_jobs(id) ON DELETE CASCADE,
                      CONSTRAINT fk_decision_ref_scope FOREIGN KEY(scope_id) REFERENCES review_decision_scopes(id),
                      CONSTRAINT ck_decision_ref_usage CHECK(usage_type='REFERENCE')
                    )
                    """);
        }
    }
}
