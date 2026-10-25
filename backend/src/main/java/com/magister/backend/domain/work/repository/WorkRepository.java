package com.magister.backend.domain.work.repository;

import com.magister.backend.domain.work.entity.Work;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface WorkRepository extends JpaRepository<Work, Long> {
    Optional<Work> findByIdAndOwner_Id(Long id, Long ownerId);

    @Query(
            """
            select w from Work w where w.owner.id = :ownerId
            and (:keyword = '' or locate(lower(:keyword), lower(w.title)) > 0)
            and (:genre = '' or w.genre = :genre)
            """)
    Page<Work> search(Long ownerId, String keyword, String genre, Pageable pageable);
}
