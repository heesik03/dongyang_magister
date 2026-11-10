package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V20__create_scene_image_jobs extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE scene_image_jobs (
                      id VARCHAR(36) PRIMARY KEY,
                      board_id VARCHAR(36) NOT NULL,
                      request_id VARCHAR(64) NOT NULL,
                      input_json LONGTEXT NOT NULL,
                      status VARCHAR(16) NOT NULL,
                      created_at TIMESTAMP(6) NOT NULL,
                      lease_until TIMESTAMP(6),
                      image_bytes MEDIUMBLOB,
                      UNIQUE (board_id, request_id),
                      FOREIGN KEY (board_id) REFERENCES scene_boards(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("CREATE INDEX ix_scene_image_dispatch ON scene_image_jobs(status, lease_until, created_at)");
            statement.execute("CREATE INDEX ix_scene_image_history ON scene_image_jobs(board_id, created_at, id)");
        }
    }
}
