package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.ReviewCandidate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewCandidateRepository extends JpaRepository<ReviewCandidate, String> {
    /** 소유권 확인된 작업만 ID 순서로 페이지 조회하며 대상 자료를 함께 읽는다. */
    @EntityGraph(attributePaths = "targetSource")
    Page<ReviewCandidate> findByJob_Id(String jobId, Pageable pageable);
}
