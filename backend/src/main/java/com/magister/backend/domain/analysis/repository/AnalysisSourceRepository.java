package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.AnalysisSource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisSourceRepository extends JpaRepository<AnalysisSource, Long> {
    /** 권한 확인한 작업의 고정 자료만 읽는다. 전체 입력 상한은 20만 자다. */
    List<AnalysisSource> findByJob_IdOrderByIdAsc(String jobId);

    /** 권한 확인한 작업에 속한 출처 ID만 조회한다. */
    Optional<AnalysisSource> findByIdAndJob_Id(Long id, String jobId);
}
