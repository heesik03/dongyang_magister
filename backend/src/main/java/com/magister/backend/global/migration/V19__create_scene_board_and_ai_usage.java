package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V19__create_scene_board_and_ai_usage extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE ai_usage_buckets (
                      user_id BIGINT NOT NULL,
                      feature VARCHAR(32) NOT NULL,
                      period_start DATE NOT NULL,
                      used_count INT NOT NULL,
                      PRIMARY KEY (user_id, feature, period_start),
                      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE scene_boards (
                      id VARCHAR(36) PRIMARY KEY,
                      work_id BIGINT NOT NULL,
                      chapter_key VARCHAR(64) NOT NULL,
                      request_id VARCHAR(64) NOT NULL,
                      selection_json LONGTEXT NOT NULL,
                      snapshot_json LONGTEXT NOT NULL,
                      result_json LONGTEXT,
                      status VARCHAR(16) NOT NULL,
                      created_at TIMESTAMP(6) NOT NULL,
                      lease_until TIMESTAMP(6),
                      UNIQUE (work_id, request_id),
                      FOREIGN KEY (work_id) REFERENCES works(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("CREATE INDEX ix_scene_history ON scene_boards(work_id, chapter_key, created_at, id)");
            statement.execute("CREATE INDEX ix_scene_dispatch ON scene_boards(status, lease_until, created_at)");
        }
    }
}
