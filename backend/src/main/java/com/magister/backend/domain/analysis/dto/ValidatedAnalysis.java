package com.magister.backend.domain.analysis.dto;

import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;

import java.util.List;

/** 공급자 응답 전체의 인용·위치 검증을 통과한 뒤에만 저장 경계로 전달한다. */
public record ValidatedAnalysis(AnalysisData result, List<AnalysisOutput.Candidate> candidates) {}
