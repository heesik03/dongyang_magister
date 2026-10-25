package com.magister.backend.domain.setting.repository;

import com.magister.backend.domain.setting.entity.*;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.Optional;

public interface WorkSettingRepository extends JpaRepository<WorkSetting, Long> {
    Optional<WorkSetting> findByIdAndWorkId(Long id, Long workId);

    @Query(
            """
            select s from WorkSetting s where s.workId = :workId
            and (:kind is null or s.kind = :kind) and (:status is null or s.status = :status)
            """)
    Page<WorkSetting> search(
            Long workId, SettingKind kind, SettingStatus status, Pageable pageable);
}
