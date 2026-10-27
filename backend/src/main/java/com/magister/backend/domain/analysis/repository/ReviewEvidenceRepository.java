package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.ReviewEvidence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewEvidenceRepository extends JpaRepository<ReviewEvidence, Long> {
    /** 현재 후보 페이지의 근거만 한 쿼리로 조회해 N+1을 방지한다. 최대 1000행이다. */
    @Query(
            "select e from ReviewEvidence e join fetch e.source where e.review.id in :reviewIds"
                + " order by e.id")
    List<ReviewEvidence> findForReviews(List<String> reviewIds);
}
