package com.magister.backend.domain.chapter.repository;

import com.magister.backend.domain.chapter.entity.Chapter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    /** 판단 근거에 실제 포함된 회차만 작품 범위 안에서 읽는다. */
    List<Chapter> findByWork_IdAndChapterKeyIn(Long workId, List<String> keys);

    /** 작품 조건을 포함해 다른 작품의 같은 공개 회차 ID와 구분한다. */
    Optional<Chapter> findByWork_IdAndChapterKey(Long workId, String chapterKey);

    /** 최대 100회차인 작품의 호환 스냅샷을 회차 번호 순서로 읽는다. */
    List<Chapter> findByWork_IdOrderByChapterNoAsc(Long workId);

    /** 본문을 포함한 회차를 요청한 페이지 크기로 제한한다. */
    Page<Chapter> findByWork_Id(Long workId, Pageable pageable);

    /** 부모 작품 잠금을 보유한 생성 경로에서 회차 수 상한을 확인한다. */
    long countByWork_Id(Long workId);
}
