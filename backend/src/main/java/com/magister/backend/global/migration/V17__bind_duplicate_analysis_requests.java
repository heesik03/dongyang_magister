package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V17__bind_duplicate_analysis_requests extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE analysis_request_aliases (
                        work_id BIGINT NOT NULL,
                        request_id VARCHAR(64) NOT NULL,
                        request_body LONGTEXT NOT NULL,
                        job_id VARCHAR(36) NOT NULL,
                        PRIMARY KEY (work_id, request_id),
                        CONSTRAINT fk_alias_work FOREIGN KEY (work_id) REFERENCES works(id) ON DELETE CASCADE,
                        CONSTRAINT fk_alias_job FOREIGN KEY (job_id) REFERENCES analysis_jobs(id) ON DELETE CASCADE
                    )
                    """);
        }
    }
}
