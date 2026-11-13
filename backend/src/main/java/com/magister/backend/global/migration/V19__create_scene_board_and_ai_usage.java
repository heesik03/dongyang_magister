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
        }
    }
}

