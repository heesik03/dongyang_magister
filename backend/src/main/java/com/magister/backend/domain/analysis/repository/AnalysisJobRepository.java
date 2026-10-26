package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.time.Instant;

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

    /** 실제 실행기에 남아 있는 자기 서버 작업만 갱신하며 만료된 lease를 되살리지 않는다. */
    @Modifying
    @Query("""
            update AnalysisJob j set j.leaseUntil = :until, j.version = j.version + 1
            where j.workerId = :workerId and j.id in :ids and j.status in :active
              and j.leaseUntil > :now
            """)
    int renewLeases(String workerId, Collection<String> ids, Collection<AnalysisJobStatus> active,
            Instant now, Instant until);

    /** 서버 시작 여부와 무관하게 lease가 만료된 미완료 작업만 복구한다. */
    @Modifying
    @Query("""
            update AnalysisJob j set j.status = :failed, j.error = :message,
              j.updatedAt = :now, j.version = j.version + 1
            where j.status in :active and (j.leaseUntil is null or j.leaseUntil <= :now)
            """)
    int failExpired(Collection<AnalysisJobStatus> active, AnalysisJobStatus failed, String message, Instant now);

    /** 다른 서버에서 취소된 로컬 작업도 실행기에 전파하기 위한 작은 ID 조회다. */
    @Query("select j.id from AnalysisJob j where j.id in :ids and j.status not in :active")
    List<String> findTerminalIds(Collection<String> ids, Collection<AnalysisJobStatus> active);

    /** 보존 기간 정리 시 원고 본문을 읽지 않고 제한된 수의 ID만 가져온다. */
    @Query("select j.id from AnalysisJob j where j.status in :terminal and j.updatedAt < :before order by j.updatedAt, j.id")
    List<String> findExpiredResultIds(Collection<AnalysisJobStatus> terminal, Instant before, Pageable limit);
}
