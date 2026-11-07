package com.magister.backend.domain.user.repository;

import com.magister.backend.domain.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    /** 정규화된 이메일의 사전 안내용 중복 여부다. 가입 경합은 DB 유일 제약이 최종 보장한다. */
    boolean existsByEmail(String email);

    /** 인증에 필요한 사용자 한 건을 조회하며 연관 작품 컬렉션은 초기화하지 않는다. */
    Optional<User> findByEmail(String email);

    interface SessionAccount {
        String getName();

        long getSecurityVersion();
    }

    /** 요청마다 인증을 복원할 때 필명과 보안 버전만 읽는다. */
    @Query("select u.name as name, u.securityVersion as securityVersion from User u where u.id = :userId")
    Optional<SessionAccount> findSessionAccount(Long userId);

    /** 비밀번호 변경과 경합해도 계정의 필명 열만 갱신한다. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update User u set u.name = :name where u.id = :userId")
    int updateName(Long userId, String name);

    /** 해시 검증 이후 다른 요청이 비밀번호를 바꿨으면 이전 버전의 갱신을 거부한다. */
    @Modifying
    @Transactional
    @Query("""
            update User u set u.passwordHash = :passwordHash, u.securityVersion = u.securityVersion + 1
            where u.id = :userId and u.securityVersion = :expectedVersion
            """)
    int updatePassword(Long userId, long expectedVersion, String passwordHash);
}
