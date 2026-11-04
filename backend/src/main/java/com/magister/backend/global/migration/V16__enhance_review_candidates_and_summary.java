package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/** 검토 후보의 4단계 구조화 설명·선택지·쟁점 그룹과 작업별 범위 요약 컬럼을 추가한다. */
public class V16__enhance_review_candidates_and_summary extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE analysis_jobs ADD summary_json LONGTEXT");
            statement.execute(
                    "ALTER TABLE review_candidates ADD issue_group_key VARCHAR(128)");
            statement.execute(
                    "ALTER TABLE review_candidates ADD issue_group_title VARCHAR(200)");
            statement.execute(
                    "ALTER TABLE review_candidates ADD confirmed_fact LONGTEXT");
            statement.execute(
                    "ALTER TABLE review_candidates ADD interpretation_text LONGTEXT");
            statement.execute(
                    "ALTER TABLE review_candidates ADD missing_info_text LONGTEXT");
            statement.execute(
                    "ALTER TABLE review_candidates ADD options_json LONGTEXT");
        }
    }
}
