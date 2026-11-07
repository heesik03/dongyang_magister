package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/** 비밀번호 변경·탈퇴 시 보안 버전을 갱신할 수 있도록 사용자 테이블에 버전 컬럼을 추가한다. */
public class V15__add_user_security_version extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute(
                    "ALTER TABLE users ADD security_version BIGINT NOT NULL DEFAULT 0");
        }
    }
}
