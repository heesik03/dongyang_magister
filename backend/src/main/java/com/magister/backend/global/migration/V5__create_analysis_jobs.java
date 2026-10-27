package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V5__create_analysis_jobs extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute(
                    "CREATE TABLE analysis_jobs(id VARCHAR(36) NOT NULL PRIMARY KEY,work_id BIGINT"
                            + " NOT NULL,request_id VARCHAR(64) NOT NULL,request_body LONGTEXT NOT"
                            + " NULL,input LONGTEXT NOT NULL,status VARCHAR(20) NOT NULL,result_json"
                            + " LONGTEXT,error_message VARCHAR(500),created_at TIMESTAMP(6) NOT"
                            + " NULL,updated_at TIMESTAMP(6) NOT NULL,version BIGINT NOT"
                            + " NULL,CONSTRAINT fk_analysis_work FOREIGN KEY(work_id) REFERENCES"
                            + " works(id),CONSTRAINT uk_analysis_request"
                            + " UNIQUE(work_id,request_id),INDEX"
                            + " ix_analysis_status(status,created_at))");
        }
    }
}
