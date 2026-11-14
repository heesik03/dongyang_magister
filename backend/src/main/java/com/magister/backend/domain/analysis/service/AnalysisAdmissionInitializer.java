package com.magister.backend.domain.analysis.service;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 스키마 생성 후 공통 접수 잠금 행을 재시작해도 한 번만 준비한다. */
@Component
@RequiredArgsConstructor
class AnalysisAdmissionInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments arguments) {
        jdbcTemplate.update("""
                INSERT INTO analysis_admission (id) VALUES (1)
                ON DUPLICATE KEY UPDATE id = id
                """);
    }
}
