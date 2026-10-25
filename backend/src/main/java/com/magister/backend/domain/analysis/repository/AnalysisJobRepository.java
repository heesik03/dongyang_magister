package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.AnalysisJob;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {
    Optional<AnalysisJob> findByWork_IdAndRequestId(Long workId, String requestId);

    long countByStatusIn(Collection<String> statuses);

    long countByWork_Owner_IdAndStatusIn(Long owner, Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AnalysisJob j where j.id=:id")
    Optional<AnalysisJob> lockById(String id);

    @Modifying
    @Query(
            "update AnalysisJob j set j.status='FAILED',j.error='서버가 재시작되었습니다. 다시 요청해 주세요.' where"
                    + " j.status in ('QUEUED','RUNNING')")
    int failInterrupted();
}
