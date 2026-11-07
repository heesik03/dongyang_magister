package com.magister.backend.domain.analysis.repository;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AnalysisRequestAliasRepository {
    private final JdbcTemplate jdbcTemplate;

    public record RequestAlias(String jobId, String requestBody) {}

    /** 같은 작품에서 이미 병합한 접수 키를 재전송했을 때 원래 작업을 찾는다. */
    public Optional<RequestAlias> find(Long workId, String requestId) {
        return jdbcTemplate.query(
                "select job_id, request_body from analysis_request_aliases where work_id = ? and request_id = ?",
                (row, index) -> new RequestAlias(row.getString("job_id"), row.getString("request_body")),
                workId, requestId).stream().findFirst();
    }

    /** 공통 접수 잠금 안에서만 동일 입력의 다른 접수 키를 기록한다. */
    public void save(Long workId, String requestId, String requestBody, String jobId) {
        jdbcTemplate.update(
                "insert into analysis_request_aliases (work_id, request_id, request_body, job_id) values (?, ?, ?, ?)",
                workId, requestId, requestBody, jobId);
    }
}
