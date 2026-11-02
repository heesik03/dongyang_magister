package com.magister.backend.domain.work.repository;

import com.magister.backend.domain.work.entity.Work;

import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface WorkRepository extends JpaRepository<Work, Long>, JpaSpecificationExecutor<Work> {
    /** 소유한 현재 페이지의 작품 요약을 한 번에 조회해 본문 로딩과 N+1을 피한다. */
    @Query("""
            select w.id as workId,
                (select count(c) from Chapter c where c.work.id = w.id) as chapterCount,
                (select count(s) from WorkSetting s where s.work.id = w.id) as settingCount,
                (select c.chapterKey from Chapter c where c.work.id = w.id
                    and c.chapterNo = (select max(latest.chapterNo) from Chapter latest
                        where latest.work.id = w.id)) as latestChapterId,
                (select c.title from Chapter c where c.work.id = w.id
                    and c.chapterNo = (select max(latest.chapterNo) from Chapter latest
                        where latest.work.id = w.id)) as latestChapterTitle
            from Work w where w.owner.id = :ownerId and w.id in :workIds
            """)
    List<WorkSummary> summarizeOwned(Long ownerId, List<Long> workIds);

    /** 인증된 소유자 조건을 항상 조회에 포함한다. */
    Optional<Work> findByIdAndOwner_Id(Long id, Long ownerId);

    /** 최초 하위 문서 생성과 부모 버전 변경을 호출자의 쓰기 트랜잭션에서 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Work w where w.id = :id and w.owner.id = :ownerId")
    Optional<Work> lockOwned(Long id, Long ownerId);

    /** 부분 문자열 검색 의미를 유지하면서 실제 지정된 필터만 질의에 넣는다. */
    default Page<Work> search(Long ownerId, String keyword, String genre, Pageable pageable) {
        Specification<Work> specification =
                (root, query, builder) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(builder.equal(root.get("owner").get("id"), ownerId));

                    if (!keyword.isEmpty()) {
                        predicates.add(
                                builder.greaterThan(
                                        builder.locate(
                                                builder.lower(root.get("title")),
                                                keyword.toLowerCase(Locale.ROOT)),
                                        0));
                    }
                    if (!genre.isEmpty()) {
                        predicates.add(builder.equal(root.get("genre"), genre));
                    }

                    return builder.and(predicates.toArray(Predicate[]::new));
                };
        return findAll(specification, pageable);
    }
}
