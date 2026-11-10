package com.magister.backend.domain.scene;

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
class SceneImageRepository {
    private static final String FIELDS = "id, input_json, status";
    private static final RowMapper<StoredImage> IMAGE = (row, index) ->
            new StoredImage(row.getString("id"), row.getString("input_json"), row.getString("status"));
    private final JdbcTemplate jdbcTemplate;

    Optional<StoredImage> findRequest(String boardId, String requestId) {
        return jdbcTemplate.query("SELECT " + FIELDS + " FROM scene_image_jobs WHERE board_id=? AND request_id=?",
                IMAGE, boardId, requestId).stream().findFirst();
    }

    List<StoredImage> list(String boardId, int page) {
        return jdbcTemplate.query("SELECT " + FIELDS + """
                 FROM scene_image_jobs WHERE board_id=? ORDER BY created_at DESC, id DESC LIMIT 10 OFFSET ?
                """, IMAGE, boardId, (long) page * 10);
    }

    Optional<StoredImage> find(String boardId, String id) {
        return jdbcTemplate.query("SELECT " + FIELDS + " FROM scene_image_jobs WHERE board_id=? AND id=?",
                IMAGE, boardId, id).stream().findFirst();
    }

    Optional<byte[]> content(String boardId, String id) {
        return jdbcTemplate.query("""
                SELECT image_bytes FROM scene_image_jobs WHERE board_id=? AND id=? AND status='SUCCEEDED'
                """, (row, index) -> row.getBytes(1), boardId, id).stream().findFirst();
    }

    void insert(String boardId, String requestId, StoredImage image) {
        jdbcTemplate.update("""
                INSERT INTO scene_image_jobs(id, board_id, request_id, input_json, status, created_at)
                VALUES (?, ?, ?, ?, 'QUEUED', ?)
                """, image.id(), boardId, requestId, image.input(), Timestamp.from(Instant.now()));
    }

    /** 이전 실행의 외부 전송 여부를 알 수 없으므로 만료 작업을 새로 실행하지 않는다. */
    void expire(Instant now) {
        jdbcTemplate.update("""
                UPDATE scene_image_jobs SET status='FAILED', lease_until=NULL
                WHERE status='RUNNING' AND lease_until < ?
                """, Timestamp.from(now));
    }

    Optional<StoredImage> claim(Instant until) {
        List<StoredImage> queue = jdbcTemplate.query("SELECT " + FIELDS + """
                 FROM scene_image_jobs WHERE status='QUEUED' ORDER BY created_at, id LIMIT 1
                """, IMAGE);
        if (queue.isEmpty()) {
            return Optional.empty();
        }
        StoredImage image = queue.getFirst();
        int updated = jdbcTemplate.update("""
                UPDATE scene_image_jobs SET status='RUNNING', lease_until=? WHERE id=? AND status='QUEUED'
                """, Timestamp.from(until), image.id());
        return updated == 1 ? Optional.of(image) : Optional.empty();
    }

    void finish(String id, String status, byte[] content) {
        jdbcTemplate.update("""
                UPDATE scene_image_jobs SET status=?, image_bytes=?, lease_until=NULL
                WHERE id=? AND status='RUNNING'
                """, status, content, id);
    }

    record StoredImage(String id, String input, String status) {}
}
