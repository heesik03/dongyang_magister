package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {
    /** 같은 작품의 멱등 키를 조회한다. 요청 본문 일치는 서비스가 검사한다. */
    Optional<AnalysisJob> findByWork_IdAndRequestId(Long workId, String requestId);

    /** 접수 잠금 안에서 한도 판단에 사용한다. 단독 count는 예약이 아니다. */
    long countByStatusIn(Collection<AnalysisJobStatus> statuses);

    /** 서로 다른 작품의 동일 사용자 작업도 함께 집계한다. */
    long countByWork_Owner_IdAndStatusIn(Long ownerId, Collection<AnalysisJobStatus> statuses);

    /** 상태 변경 전용 잠금이다. 폴링에는 사용하지 않는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AnalysisJob j where j.id=:id")
    Optional<AnalysisJob> lockById(String id);

    /** 입력 원고·요청 본문을 제외하고 소유권도 동일 질의에서 확인한다. */
    @Query("""
            select j.id as id, j.status as status, j.error as error, j.result as result
            from AnalysisJob j
            where j.id = :jobId and j.work.id = :workId and j.work.owner.id = :ownerId
            """)
    Optional<AnalysisJobView> findOwnedView(String jobId, Long workId, Long ownerId);
}
