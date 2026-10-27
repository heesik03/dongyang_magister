package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V4__create_workspaces extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute(
                    "CREATE TABLE workspaces (work_id BIGINT NOT NULL PRIMARY KEY,content LONGTEXT"
                            + " NOT NULL,revision BIGINT NOT NULL,mutation_id VARCHAR(64),updated_at"
                            + " TIMESTAMP(6) NOT NULL,version BIGINT NOT NULL,CONSTRAINT"
                            + " fk_workspace_work FOREIGN KEY(work_id) REFERENCES works(id))");
        }
    }
}
