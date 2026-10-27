package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V6__index_setting_kind extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            // MySQL EXPLAIN: 1만 건, 작품당 100건에서 kind 필터의 추정 스캔이 100 -> 25건.
            statement.execute(
                    "CREATE INDEX ix_settings_work_kind_id ON work_settings(work_id,kind,id)");
        }
    }
}
