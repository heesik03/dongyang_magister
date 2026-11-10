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
    private static final RowMapper<StoredImage> IMAGE =
            (row, index) ->
                    new StoredImage(
                            row.getString("id"),
                            row.getString("input_json"),
                            row.getString("status"));
    private final JdbcTemplate jdbcTemplate;

    /** 동일 장면 보드와 요청 ID로 접수된 기존 콘셉트 이미지 작업을 조회한다. */
    Optional<StoredImage> findRequest(String boardId, String requestId) {
        return jdbcTemplate
                .query(
                        "SELECT "
                                + FIELDS
                                + " FROM scene_image_jobs WHERE board_id=? AND request_id=?",
                        IMAGE,
                        boardId,
                        requestId)
                .stream()
                .findFirst();
    }

    /** 장면 보드에 속한 콘셉트 이미지 작업 메타데이터를 최신순으로 페이지당 최대 10개 조회한다. */
    List<StoredImage> list(String boardId, int page) {
        return jdbcTemplate.query(
                "SELECT "
                        + FIELDS
                        + """
                         FROM scene_image_jobs WHERE board_id=? ORDER BY created_at DESC, id DESC LIMIT 10 OFFSET ?
                        """,
                IMAGE,
                boardId,
                (long) page * 10);
    }

    /** 장면 보드에 속한 개별 콘셉트 이미지 작업 메타데이터를 조회한다. */
    Optional<StoredImage> find(String boardId, String id) {
        return jdbcTemplate
                .query(
                        "SELECT " + FIELDS + " FROM scene_image_jobs WHERE board_id=? AND id=?",
                        IMAGE,
                        boardId,
                        id)
                .stream()
                .findFirst();
    }

    /** 완료(SUCCEEDED)된 콘셉트 이미지 작업의 PNG 바이트를 조회한다. */
    Optional<byte[]> content(String boardId, String id) {
        return jdbcTemplate
                .query(
                        """
                        SELECT image_bytes FROM scene_image_jobs WHERE board_id=? AND id=? AND status='SUCCEEDED'
                        """,
                        (row, index) -> row.getBytes(1),
                        boardId,
                        id)
                .stream()
                .findFirst();
    }

    /** 신규 콘셉트 이미지 작업을 대기(QUEUED) 상태로 저장한다. */
    void insert(String boardId, String requestId, StoredImage image) {
        jdbcTemplate.update(
                """
                INSERT INTO scene_image_jobs(id, board_id, request_id, input_json, status, created_at)
                VALUES (?, ?, ?, ?, 'QUEUED', ?)
                """,
                image.id(),
                boardId,
                requestId,
                image.input(),
                Timestamp.from(Instant.now()));
    }

    /** 이전 실행의 외부 전송 여부를 알 수 없으므로 만료 작업을 새로 실행하지 않는다. */
    void expire(Instant now) {
        jdbcTemplate.update(
                """
                UPDATE scene_image_jobs SET status='FAILED', lease_until=NULL
                WHERE status='RUNNING' AND lease_until < ?
                """,
                Timestamp.from(now));
    }

    /** 대기 중인 가장 오래된 콘셉트 이미지 작업 1건을 RUNNING 상태로 원자적으로 선점한다. */
    Optional<StoredImage> claim(Instant until) {
        List<StoredImage> queue =
                jdbcTemplate.query(
                        "SELECT "
                                + FIELDS
                                + """
                                 FROM scene_image_jobs WHERE status='QUEUED' ORDER BY created_at, id LIMIT 1
                                """,
                        IMAGE);
        if (queue.isEmpty()) {
            return Optional.empty();
        }

        StoredImage image = queue.getFirst();
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE scene_image_jobs SET status='RUNNING', lease_until=? WHERE id=? AND status='QUEUED'
                        """,
                        Timestamp.from(until),
                        image.id());
        if (updated != 1) {
            return Optional.empty();
        }
        return Optional.of(new StoredImage(image.id(), image.input(), "RUNNING"));
    }

    /** 선점되어 실행 중인 콘셉트 이미지 작업의 최종 상태와 PNG 바이트를 저장한다. */
    void finish(String id, String status, byte[] content) {
        jdbcTemplate.update(
                """
                UPDATE scene_image_jobs SET status=?, image_bytes=?, lease_until=NULL
                WHERE id=? AND status='RUNNING'
                """,
                status,
                content,
                id);
    }

    record StoredImage(String id, String input, String status) {}
}
