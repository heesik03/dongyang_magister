package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.ReviewCandidate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ReviewCandidateRepository extends JpaRepository<ReviewCandidate, String> {
    /** 후보·작품·소유자를 함께 검사하고 불변 원문을 트랜잭션 안에서 읽는다. */
    @Query(
            "select r from ReviewCandidate r join fetch r.targetSource join fetch r.job j where"
                    + " r.id=:reviewId and j.work.id=:workId and j.work.owner.id=:ownerId")
    Optional<ReviewCandidate> findOwned(String reviewId, Long workId, Long ownerId);

    /** 소유권 확인된 작업만 ID 순서로 페이지 조회하며 대상 자료를 함께 읽는다. */
    @EntityGraph(attributePaths = "targetSource")
    Page<ReviewCandidate> findByJob_Id(String jobId, Pageable pageable);
}
