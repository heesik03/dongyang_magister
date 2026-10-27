package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/** 검토 근거 및 작가 판단 범위 조회·정렬을 위한 인덱스를 보강한다. */
public class V13__index_review_and_decision_queries extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("CREATE INDEX ix_evidence_job ON review_evidences(job_id, id)");
            statement.execute(
                    "CREATE INDEX ix_scope_decision ON review_decision_scopes(decision_id, id)");
            statement.execute(
                    "CREATE INDEX ix_decision_status_id ON review_decisions(status, id)");
        }
    }
}
