package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/** 기존 Workspace 사건을 이동하지 않고 추출 원고·후보·작업만 별도 영속화한다. */
public class V14__create_event_extractions extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE event_extractions (
                      id VARCHAR(36) PRIMARY KEY,
                      work_id BIGINT NOT NULL,
                      request_id VARCHAR(64) NOT NULL,
                      request_body LONGTEXT NOT NULL,
                      input_json LONGTEXT NOT NULL,
                      result_json LONGTEXT NOT NULL,
                      status VARCHAR(20) NOT NULL,
                      error_message VARCHAR(500),
                      created_at TIMESTAMP(6) NOT NULL,
                      lease_until TIMESTAMP(6) NOT NULL,
                      version BIGINT NOT NULL DEFAULT 0,
                      CONSTRAINT fk_extraction_work FOREIGN KEY (work_id) REFERENCES works(id) ON DELETE CASCADE,
                      CONSTRAINT uk_extraction_request UNIQUE (work_id,request_id)
                    )
                    """);
            statement.execute(
                    "CREATE INDEX ix_extraction_history ON"
                            + " event_extractions(work_id,created_at,id)");
            statement.execute(
                    "CREATE INDEX ix_extraction_work_status ON"
                            + " event_extractions(work_id,status)");
            statement.execute(
                    "CREATE INDEX ix_extraction_active ON event_extractions(status,lease_until)");
        }
    }
}
