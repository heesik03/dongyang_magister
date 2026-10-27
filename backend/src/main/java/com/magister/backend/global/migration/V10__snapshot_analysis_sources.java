package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class V10__snapshot_analysis_sources extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean mysql = connection.getMetaData().getDatabaseProductName().equals("MySQL");
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE analysis_jobs ADD target_chapter_id BIGINT");
            statement.execute(
                    "ALTER TABLE analysis_jobs ADD chapter_key_snapshot VARCHAR(64)"
                            + (mysql ? " CHARACTER SET utf8mb4 COLLATE utf8mb4_bin" : ""));
            statement.execute("ALTER TABLE analysis_jobs ADD chapter_title_snapshot VARCHAR(100)");
            statement.execute("ALTER TABLE analysis_jobs ADD policy_version VARCHAR(50)");
            statement.execute("ALTER TABLE analysis_jobs ADD provider_model VARCHAR(200)");
            statement.execute(
                    "ALTER TABLE analysis_jobs ADD source_format_version INT NOT NULL DEFAULT 0");
            statement.execute(
                    "ALTER TABLE analysis_jobs ADD CONSTRAINT fk_analysis_chapter FOREIGN"
                            + " KEY(target_chapter_id) REFERENCES chapters(id) ON DELETE SET NULL");
            statement.execute(
                    "CREATE INDEX ix_analysis_work_created ON"
                            + " analysis_jobs(work_id,created_at,id)");
            statement.execute(
                    "CREATE INDEX ix_analysis_chapter_created ON"
                            + " analysis_jobs(work_id,chapter_key_snapshot,created_at,id)");
            String sourceSchema =
                    """
                    CREATE TABLE analysis_sources (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      job_id VARCHAR(36) NOT NULL,
                      chapter_id BIGINT,
                      setting_id BIGINT,
                      source_type VARCHAR(20) NOT NULL,
                      source_key VARCHAR(64) NOT NULL,
                      source_revision BIGINT NOT NULL,
                      title_snapshot VARCHAR(100) NOT NULL,
                      content_snapshot LONGTEXT NOT NULL,
                      metadata_snapshot LONGTEXT NOT NULL,
                      content_hash VARCHAR(64) NOT NULL,
                      CONSTRAINT fk_source_job FOREIGN KEY(job_id) REFERENCES analysis_jobs(id) ON DELETE CASCADE,
                      CONSTRAINT fk_source_chapter FOREIGN KEY(chapter_id) REFERENCES chapters(id),
                      CONSTRAINT fk_source_setting FOREIGN KEY(setting_id) REFERENCES work_settings(id),
                      CONSTRAINT ck_source_type CHECK(source_type IN ('TARGET','PREVIOUS','SETTING')),
                      CONSTRAINT ck_source_reference CHECK ((source_type = 'SETTING' AND chapter_id IS NULL AND setting_id IS NOT NULL) OR (source_type <> 'SETTING' AND setting_id IS NULL AND chapter_id IS NOT NULL)),
                      CONSTRAINT uk_source_identity UNIQUE(job_id,source_type,source_key)
                    )
                    """;
            if (mysql) {
                sourceSchema =
                        sourceSchema.replace(
                                "source_key VARCHAR(64)",
                                "source_key VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            }
            statement.execute(sourceSchema);
        }
        // 과거 작업의 원문은 기존 input 그대로 보존한다. 없던 개별 근거 ID를 만들어 내지 않는다.
        ObjectMapper mapper = new ObjectMapper();
        try (PreparedStatement read =
                        connection.prepareStatement(
                                "SELECT id,input FROM analysis_jobs WHERE id>? ORDER BY id LIMIT"
                                        + " 20");
                PreparedStatement update =
                        connection.prepareStatement(
                                "UPDATE analysis_jobs SET"
                                        + " chapter_key_snapshot=?,chapter_title_snapshot=? WHERE"
                                        + " id=?")) {
            String cursor = "";
            int count;
            do {
                read.setString(1, cursor);
                count = 0;
                try (ResultSet rows = read.executeQuery()) {
                    while (rows.next()) {
                        cursor = rows.getString("id");
                        count++;
                        try {
                            JsonNode chapter =
                                    mapper.readTree(rows.getString("input")).path("chapter");
                            JsonNode key = chapter.path("id");
                            JsonNode title = chapter.path("title");
                            if (key.isString()
                                    && title.isString()
                                    && key.asString().length() <= 64
                                    && title.asString().length() <= 100) {
                                update.setString(1, key.asString());
                                update.setString(2, title.asString());
                                update.setString(3, rows.getString("id"));
                                update.executeUpdate();
                            }
                        } catch (JacksonException invalidLegacyInput) {
                            // 손상된 과거 작업은 회차 미상인 기존 기록으로 남긴다. 원문/오류 전문은 출력하지 않는다.
                        }
                    }
                }
            } while (count == 20);
        }
    }
}
