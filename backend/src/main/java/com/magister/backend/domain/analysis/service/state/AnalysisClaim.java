package com.magister.backend.domain.analysis.service.state;

import com.magister.backend.domain.analysis.dto.AnalysisInput;

/** 트랜잭션 밖으로 엔티티 대신 불변 입력을 전달한다. */
public record AnalysisClaim(Long ownerId, AnalysisInput input) {}
