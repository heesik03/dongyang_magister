package com.magister.backend.domain.decision.repository;

import com.magister.backend.domain.decision.entity.ReviewDecision;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReviewDecisionRepository extends JpaRepository<ReviewDecision, Long> {
    /** 부모 작품 잠금 아래에서 판단의 멱등 키를 대조한다. */
    Optional<ReviewDecision> findByReview_IdAndMutationId(String reviewId, String mutationId);

    /** 최신 버전 하나만 조회한다. 저장 호출은 부모 작품 잠금을 먼저 보유한다. */
    Optional<ReviewDecision> findFirstByReview_IdOrderByDecisionVersionDesc(String reviewId);

    /** 소유권 확인한 후보의 판단 이력을 버전 내림차순으로 제한한다. */
    Page<ReviewDecision> findByReview_Id(String reviewId, Pageable page);

    /** 후보 페이지의 최신 판단을 한 번에 읽는다. */
    @Query(
            "select d from ReviewDecision d join fetch d.review r join fetch r.targetSource where"
                + " d.review.id in :reviewIds and d.decisionVersion=(select max(v.decisionVersion)"
                + " from ReviewDecision v where v.review=d.review)")
    List<ReviewDecision> findLatestForReviews(List<String> reviewIds);

    /** 작품의 최신 판단 목록이며 철회도 숨기지 않는다. */
    @Query(
            "select d from ReviewDecision d join fetch d.review r join fetch r.targetSource where"
                + " r.job.work.id=:workId and d.decisionVersion=(select max(v.decisionVersion) from"
                + " ReviewDecision v where v.review=d.review) and (:status is null or"
                + " d.status=:status) and (:chapterKey is null or"
                + " r.targetSource.sourceKey=:chapterKey)")
    Page<ReviewDecision> findLatestForWork(
            Long workId, String chapterKey, String status, Pageable page);

    /** 현재 회차·참고 자료와 관련된 최신 판단을 페이지 단위로 조회한다. */
    @Query(
            """
            select d from ReviewDecision d join fetch d.review r join fetch r.targetSource t
            where r.job.work.id=:workId and d.status<>'미검토'
              and d.decisionVersion=(select max(v.decisionVersion) from ReviewDecision v where v.review=d.review)
              and exists(select s.id from ReviewDecisionScope s left join s.fromChapter fc left join s.toChapter tc left join s.sceneSource ss where s.decision=d and
                ((s.scopeType='CHAPTER_RANGE' and fc.chapterNo<=:chapterNo and
                  (tc is null or tc.chapterNo>=:chapterNo)) or
                 (s.scopeType='SCENE' and ss.chapter.id=:chapterId)))
              and exists(select e.id from ReviewEvidence e where e.review=r and
                ((e.source.type='SETTING' and e.source.sourceKey in :settingKeys) or
                 (e.source.type='PREVIOUS' and e.source.sourceKey in :previousKeys)))
              and not exists(select e.id from ReviewEvidence e where e.review=r and
                not ((e.source.type='SETTING' and e.source.sourceKey in :settingKeys) or
                     (e.source.type='PREVIOUS' and e.source.sourceKey in :previousKeys)))
            order by d.id
            """)
    List<ReviewDecision> findApplicable(
            Long workId,
            Long chapterId,
            int chapterNo,
            List<String> settingKeys,
            List<String> previousKeys,
            Pageable limit);
}
