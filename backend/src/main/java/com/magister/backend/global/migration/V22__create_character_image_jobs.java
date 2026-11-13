package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V22__create_character_image_jobs extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        boolean mysql = "MySQL".equals(context.getConnection().getMetaData().getDatabaseProductName());
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE character_image_jobs (
                        id VARCHAR(36) PRIMARY KEY,
                        setting_id BIGINT NOT NULL,
                        setting_version BIGINT NOT NULL,
                        request_id VARCHAR(64) NOT NULL,
                        prompt TEXT NOT NULL,
                        request_fingerprint VARCHAR(64) NOT NULL,
                        model VARCHAR(128) NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        is_primary BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMP(6) NOT NULL,
                        lease_until TIMESTAMP(6),
                        mime_type VARCHAR(32),
                        image_bytes MEDIUMBLOB,
                        UNIQUE (setting_id, request_id),
                        FOREIGN KEY (setting_id) REFERENCES work_settings(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("CREATE INDEX ix_character_image_dispatch ON character_image_jobs(status, created_at, id)");
            statement.execute("CREATE INDEX ix_character_image_history ON character_image_jobs(setting_id, created_at, id)");

            // 삭제된 설정도 과거 검토에 저장된 근거 스냅샷은 읽을 수 있어야 한다.
            statement.execute("ALTER TABLE analysis_sources DROP "
                    + (mysql ? "CHECK" : "CONSTRAINT") + " ck_source_reference");
            statement.execute("ALTER TABLE analysis_sources DROP "
                    + (mysql ? "FOREIGN KEY" : "CONSTRAINT") + " fk_source_setting");
            statement.execute("""
                    ALTER TABLE analysis_sources ADD CONSTRAINT fk_source_setting
                    FOREIGN KEY (setting_id) REFERENCES work_settings(id) ON DELETE SET NULL
                    """);
            statement.execute("""
                    ALTER TABLE analysis_sources ADD CONSTRAINT ck_source_reference CHECK (
                        (source_type = 'SETTING' AND chapter_id IS NULL)
                        OR (source_type <> 'SETTING' AND chapter_id IS NOT NULL)
                    )
                    """);
        }
    }
}
