package com.magister.backend.domain.user.repository;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 회원 탈퇴 시 사용자 행과 소유 작품을 일정한 순서로 잠그고 하위 자료 정리 후 계정을 삭제한다. */
@Repository
@RequiredArgsConstructor
public class UserDeletionRepository {
    private final JdbcTemplate jdbcTemplate;

    public record LockedUser(Long id, String passwordHash) {}

    /** 탈퇴 대상 사용자 행을 잠그고 비밀번호 해시를 조회한다. */
    public Optional<LockedUser> lockUser(Long userId) {
        List<LockedUser> rows =
                jdbcTemplate.query(
                        "SELECT id, password_hash FROM users WHERE id = ? FOR UPDATE",
                        (row, index) ->
                                new LockedUser(
                                        row.getLong("id"), row.getString("password_hash")),
                        userId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    /** 사용자가 소유한 작품 행을 ID 오름차순으로 잠근다. */
    public List<Long> lockOwnedWorkIds(Long ownerId) {
        return jdbcTemplate.query(
                "SELECT id FROM works WHERE owner_id = ? ORDER BY id FOR UPDATE",
                (row, index) -> row.getLong("id"),
                ownerId);
    }

    /** 소유 작품과 하위 자료가 모두 제거된 뒤 사용자 계정을 삭제한다. */
    public void deleteUser(Long userId) {
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }
}
