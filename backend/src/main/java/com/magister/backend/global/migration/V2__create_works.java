package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.*;

import java.sql.Statement;

public class V2__create_works extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE works (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        owner_id BIGINT NOT NULL,
                        title VARCHAR(100) NOT NULL,
                        genre VARCHAR(30) NOT NULL,
                        era VARCHAR(100) NOT NULL,
                        description VARCHAR(500) NOT NULL,
                        created_at TIMESTAMP(6) NOT NULL,
                        updated_at TIMESTAMP(6) NOT NULL,
                        version BIGINT NOT NULL,
                        CONSTRAINT fk_works_owner FOREIGN KEY (owner_id) REFERENCES users(id),
                        INDEX ix_works_owner_updated (owner_id, updated_at, id)
                    )
                    """);
        }
    }
}
