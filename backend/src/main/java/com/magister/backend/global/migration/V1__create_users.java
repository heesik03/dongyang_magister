package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V1__create_users extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE users (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        email VARCHAR(254) NOT NULL,
                        name VARCHAR(30) NOT NULL,
                        password_hash VARCHAR(255) NOT NULL,
                        CONSTRAINT uk_users_email UNIQUE (email)
                    )
                    """);
        }
    }
}
