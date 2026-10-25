package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.*;

import java.sql.Statement;

public class V3__create_work_settings extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE work_settings (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        work_id BIGINT NOT NULL,
                        title VARCHAR(100) NOT NULL,
                        kind VARCHAR(30) NOT NULL,
                        status VARCHAR(30) NOT NULL,
                        content TEXT NOT NULL,
                        created_at TIMESTAMP(6) NOT NULL,
                        updated_at TIMESTAMP(6) NOT NULL,
                        version BIGINT NOT NULL,
                        CONSTRAINT fk_settings_work FOREIGN KEY (work_id) REFERENCES works(id),
                        CONSTRAINT ck_setting_kind CHECK (kind IN ('CHARACTER', 'WORLD', 'MAJOR_EVENT', 'INTENDED_ADAPTATION')),
                        CONSTRAINT ck_setting_status CHECK (status IN ('CONFIRMED', 'IDEA', 'AI_CANDIDATE')),
                        INDEX ix_settings_work_id (work_id, id)
                    )
                    """);
        }
    }
}
