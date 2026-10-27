package com.magister.backend.domain.analysis.repository;

import com.magister.backend.domain.analysis.entity.AnalysisAdmission;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AnalysisAdmissionRepository extends JpaRepository<AnalysisAdmission, Long> {
    /** 작품 잠금보다 먼저 획득해 사용자별·전체 count와 insert를 하나로 묶는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AnalysisAdmission a where a.id = 1")
    Optional<AnalysisAdmission> lockAdmission();
}
