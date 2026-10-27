package com.magister.backend.domain.user.repository;

import com.magister.backend.domain.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    /** 정규화된 이메일의 사전 안내용 중복 여부다. 가입 경합은 DB 유일 제약이 최종 보장한다. */
    boolean existsByEmail(String email);

    /** 인증에 필요한 사용자 한 건을 조회하며 연관 작품 컬렉션은 초기화하지 않는다. */
    Optional<User> findByEmail(String email);
}
