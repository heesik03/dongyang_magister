package com.magister.backend.domain.analysis.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 서버가 여러 개여도 분석 접수의 검사와 생성을 직렬화하는 단일 행이다. */
@Entity
@Table(name = "analysis_admission")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisAdmission {
    @Id private Long id;
}
