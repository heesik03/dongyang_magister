package com.magister.backend.domain.decision.repository;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AnalysisDecisionReferenceRepository {
    private final JdbcTemplate jdbcTemplate;

    /** 접수 트랜잭션 안에서 전송한 범위 ID만 일괄 고정한다. 복합 PK로 중복 참조를 거절한다. */
    public void save(String jobId, List<Long> scopeIds) {
        if (!scopeIds.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO analysis_decision_references(job_id,scope_id,usage_type)"
                            + " VALUES(?,?,'REFERENCE')",
                    scopeIds,
                    100,
                    (statement, scopeId) -> {
                        statement.setString(1, jobId);
                        statement.setLong(2, scopeId);
                    });
        }
    }
}
