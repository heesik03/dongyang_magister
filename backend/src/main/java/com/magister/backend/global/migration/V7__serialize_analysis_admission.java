package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.Statement;

public class V7__serialize_analysis_admission extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("CREATE TABLE analysis_admission (id BIGINT NOT NULL PRIMARY KEY)");
            statement.execute("INSERT INTO analysis_admission (id) VALUES (1)");
            statement.execute("ALTER TABLE analysis_jobs ADD CONSTRAINT ck_analysis_status CHECK "
                    + "(status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED'))");
        }
    }
}
