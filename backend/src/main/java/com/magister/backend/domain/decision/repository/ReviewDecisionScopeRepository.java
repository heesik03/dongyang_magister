package com.magister.backend.domain.decision.repository;

import com.magister.backend.domain.decision.entity.ReviewDecisionScope;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewDecisionScopeRepository extends JpaRepository<ReviewDecisionScope, Long> {
    /** 제한된 판단 페이지의 적용 범위를 N+1 없이 읽는다. */
    @Query(
            "select s from ReviewDecisionScope s left join fetch s.fromChapter left join fetch"
                + " s.toChapter left join fetch s.sceneSource where s.decision.id in :decisionIds"
                + " order by s.id")
    List<ReviewDecisionScope> findForDecisions(List<Long> decisionIds);
}
