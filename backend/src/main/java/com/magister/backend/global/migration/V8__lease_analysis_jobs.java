package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.Statement;

public class V8__lease_analysis_jobs extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE analysis_jobs ADD worker_id VARCHAR(36)");
            statement.execute("ALTER TABLE analysis_jobs ADD lease_until TIMESTAMP(6)");
            statement.execute("CREATE INDEX ix_analysis_lease ON analysis_jobs(status,lease_until)");
            statement.execute("CREATE INDEX ix_analysis_retention ON analysis_jobs(status,updated_at,id)");
        }
    }
}
