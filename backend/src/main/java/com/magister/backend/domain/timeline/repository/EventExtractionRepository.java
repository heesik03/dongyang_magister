package com.magister.backend.domain.timeline.repository;

import com.magister.backend.domain.timeline.entity.EventExtraction;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventExtractionRepository extends JpaRepository<EventExtraction, String> {
    /** 동일 작품의 요청 키를 재사용해 중복 공급자 호출을 막는다. */
    Optional<EventExtraction> findByWork_IdAndRequestId(Long workId, String requestId);

    /** 원고를 제외한 이력을 페이지당 최대 5개로 제한한다. */
    @Query(
            "select e.id as id,e.status as status,e.version as version,e.createdAt as"
                    + " createdAt,e.error as error,e.result as result from EventExtraction e where"
                    + " e.work.id=:workId order by e.createdAt desc,e.id desc")
    Page<ExtractionView> history(@Param("workId") Long workId, Pageable page);

    /** 작업 상태·후보 폴링은 원고와 요청 본문을 읽지 않는다. */
    @Query(
            "select e.id as id,e.status as status,e.version as version,e.createdAt as"
                    + " createdAt,e.error as error,e.result as result from EventExtraction e where"
                    + " e.id=:id and e.work.id=:workId")
    Optional<ExtractionView> view(@Param("id") String id, @Param("workId") Long workId);

    /** 작업 변경은 부모 작품 잠금 이후 이 행을 잠근다. 워커는 작업 행만 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EventExtraction e where e.id=:id")
    Optional<EventExtraction> lockById(@Param("id") String id);

    /** 공통 접수 잠금을 보유한 상태에서 사용자별 슬롯을 계산한다. */
    long countByWork_Owner_IdAndStatusIn(Long ownerId, List<String> statuses);

    /** 기존 분석과 같은 전역 실행 한도를 사용한다. */
    long countByStatusIn(List<String> statuses);

    /** 작품 삭제 커밋 뒤 로컬에서 실행 중인 사건 추출도 취소하기 위한 ID 목록이다. */
    @Query(
            "select e.id from EventExtraction e where e.work.id=:workId and e.status in"
                    + " ('QUEUED','RUNNING')")
    List<String> findActiveIds(@Param("workId") Long workId);

    /** 사라진 실행기의 작업을 실패로 바꾸되 후보와 원고를 삭제하지 않는다. */
    @Modifying
    @Query(
            "update EventExtraction e set e.status='FAILED',e.error='작업 실행 시간이 만료되었습니다. 다시 요청해"
                    + " 주세요.',e.version=e.version+1 where e.status in ('QUEUED','RUNNING') and"
                    + " e.leaseUntil < :now")
    int expire(@Param("now") Instant now);
}
