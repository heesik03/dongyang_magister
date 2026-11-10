package com.magister.backend.domain.scene;

import com.magister.backend.domain.scene.SceneBoardData.Reference;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class SceneBoardRepository {
    private static final RowMapper<StoredBoard> BOARD = (row, index) -> new StoredBoard(
            row.getString("id"), row.getString("selection_json"), row.getString("snapshot_json"),
            row.getString("status"), row.getString("result_json"));
    private final JdbcTemplate jdbcTemplate;

    List<Reference> findEnabledReferenceCards() {
        return jdbcTemplate.query("""
                SELECT id, title, author, location, source_url, technique
                FROM scene_reference_cards WHERE enabled=TRUE ORDER BY id LIMIT 30
                """, (row, index) -> new Reference(row.getString("id"), row.getString("title"),
                row.getString("author"), row.getString("location"), row.getString("source_url"),
                row.getString("technique")));
    }

    Optional<StoredBoard> findRequest(long workId, String requestId) {
        return jdbcTemplate.query("SELECT * FROM scene_boards WHERE work_id=? AND request_id=?",
                BOARD, workId, requestId).stream().findFirst();
    }

    Optional<StoredBoard> find(long workId, String id) {
        return jdbcTemplate.query("SELECT * FROM scene_boards WHERE work_id=? AND id=?",
                BOARD, workId, id).stream().findFirst();
    }

    List<String> listIds(long workId, String chapterId, int page) {
        return jdbcTemplate.queryForList("""
                SELECT id FROM scene_boards WHERE work_id=? AND chapter_key=?
                ORDER BY created_at DESC, id DESC LIMIT 10 OFFSET ?
                """, String.class, workId, chapterId, (long) page * 10);
    }

    void insert(long workId, String chapterId, String requestId, StoredBoard board) {
        jdbcTemplate.update("""
                INSERT INTO scene_boards
                    (id, work_id, chapter_key, request_id, selection_json, snapshot_json, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'QUEUED', ?)
                """, board.id(), workId, chapterId, requestId, board.selection(), board.snapshot(),
                Timestamp.from(Instant.now()));
    }

    /** 이미 시작한 공급자 호출은 전송 여부를 알 수 없어 자동 재실행하지 않는다. */
    void expire(Instant now) {
        jdbcTemplate.update("""
                UPDATE scene_boards SET status='FAILED', lease_until=NULL
                WHERE status='RUNNING' AND lease_until < ?
                """, Timestamp.from(now));
    }

    Optional<StoredBoard> claimNext(Instant leaseUntil) {
        List<StoredBoard> pending = jdbcTemplate.query("""
                SELECT * FROM scene_boards WHERE status='QUEUED' ORDER BY created_at, id LIMIT 1
                """, BOARD);
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        StoredBoard next = pending.getFirst();
        int claimed = jdbcTemplate.update("""
                UPDATE scene_boards SET status='RUNNING', lease_until=? WHERE id=? AND status='QUEUED'
                """, Timestamp.from(leaseUntil), next.id());
        return claimed == 1 ? Optional.of(next) : Optional.empty();
    }

    void finish(String id, String status, String result) {
        jdbcTemplate.update("""
                UPDATE scene_boards SET status=?, result_json=?, lease_until=NULL
                WHERE id=? AND status='RUNNING'
                """, status, result, id);
    }

    record StoredBoard(String id, String selection, String snapshot, String status, String result) {}
}
