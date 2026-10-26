package com.magister.backend.domain.setting.repository;

import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkSettingRepository
        extends JpaRepository<WorkSetting, Long>, JpaSpecificationExecutor<WorkSetting> {
    /** 선택한 설정을 작품 범위 안에서 한 번에 읽어 분석 입력의 N+1을 방지한다. */
    List<WorkSetting> findAllByWork_IdAndIdIn(Long workId, Collection<Long> ids);

    /** 다른 작품에 속한 ID를 현재 작품의 설정으로 접근하지 못하게 한다. */
    Optional<WorkSetting> findByIdAndWork_Id(Long id, Long workId);

    /** 전체 개수가 필요한 기존 오프셋 계약을 유지한다. 없는 필터의 OR 조건은 만들지 않는다. */
    default Page<WorkSetting> search(
            Long workId, SettingKind kind, SettingStatus status, Pageable pageable) {
        return findAll(matching(workId, kind, status, null), pageable);
    }

    /** ID 내림차순 키셋 조회다. count 없이 size+1개만 읽는다. */
    default List<WorkSetting> searchAfter(
            Long workId, SettingKind kind, SettingStatus status, Long beforeId, int size) {
        return findBy(
                matching(workId, kind, status, beforeId),
                query -> query.sortBy(Sort.by("id").descending()).limit(size + 1).all());
    }

    private static Specification<WorkSetting> matching(
            Long workId, SettingKind kind, SettingStatus status, Long beforeId) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("work").get("id"), workId));

            if (kind != null) {
                predicates.add(builder.equal(root.get("kind"), kind));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (beforeId != null) {
                predicates.add(builder.lessThan(root.get("id"), beforeId));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
