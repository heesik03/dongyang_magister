package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.AnalysisSource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisSourceRepository extends JpaRepository<AnalysisSource, Long> {
    /** 권한 확인한 작업의 고정 자료만 읽는다. 전체 입력 상한은 20만 자다. */
    List<AnalysisSource> findByJob_IdOrderByIdAsc(String jobId);
}
