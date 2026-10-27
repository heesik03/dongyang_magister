package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.AnalysisSource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AnalysisSourceRepository extends JpaRepository<AnalysisSource, Long> {
    /** 권한 확인한 작업의 고정 자료만 읽는다. 전체 입력 상한은 20만 자다. */
    List<AnalysisSource> findByJob_IdOrderByIdAsc(String jobId);

    /** 권한 확인한 작업에 속한 출처 ID만 조회한다. */
    Optional<AnalysisSource> findByIdAndJob_Id(Long id, String jobId);

    /** 작업 본문(LONGTEXT)을 읽지 않고 소유권과 작업 소속을 함께 검증해 출처 원문을 단건 조회한다. */
    @Query(
            "select s from AnalysisSource s where s.id=:id and s.job.id=:jobId and"
                    + " s.job.work.id=:workId and s.job.work.owner.id=:ownerId")
    Optional<AnalysisSource> findOwnedSource(Long id, String jobId, Long workId, Long ownerId);
}
