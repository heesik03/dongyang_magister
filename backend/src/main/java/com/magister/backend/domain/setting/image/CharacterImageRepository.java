package com.magister.backend.domain.setting.image;

import com.magister.backend.domain.setting.image.CharacterImageData.Job;
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
class CharacterImageRepository {
    private static final String METADATA = "id, setting_version, status, is_primary, created_at";
    private static final RowMapper<Job> JOB = (row, index) -> new Job(
            row.getString("id"), row.getLong("setting_version"), row.getString("status"),
            row.getBoolean("is_primary"), row.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbcTemplate;

    /** 목록에는 이미지 바이트와 생성 프롬프트를 포함하지 않는다. */
    List<Job> list(long settingId, int limit) {
        return jdbcTemplate.query("SELECT " + METADATA + """
                 FROM character_image_jobs WHERE setting_id=? AND status<>'DELETED'
                 ORDER BY created_at DESC, id DESC LIMIT ?
                """, JOB, settingId, limit);
    }

    /** 삭제된 시안도 재전송으로 재접수되지 않게 작업 식별자를 보존한다. */
    Optional<Receipt> receipt(long settingId, String requestId) {
        return jdbcTemplate.query("SELECT " + METADATA + """
                 , request_fingerprint FROM character_image_jobs WHERE setting_id=? AND request_id=?
                """, (row, index) -> new Receipt(JOB.mapRow(row, index), row.getString("request_fingerprint")),
                settingId, requestId).stream().findFirst();
    }

    /** 작업을 접수하며 이미지 생성은 커밋 후 워커가 수행한다. */
    void insert(long settingId, String requestId, String fingerprint, String prompt, String model, Job job) {
        jdbcTemplate.update("""
                INSERT INTO character_image_jobs
                    (id, setting_id, setting_version, request_id, request_fingerprint, prompt, model, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, job.id(), settingId, job.settingVersion(), requestId, fingerprint, prompt, model,
                job.status(), Timestamp.from(job.createdAt()));
    }

    /** 활성 작업 및 보관 중인 시안 수를 계산한다. */
    int count(long settingId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM character_image_jobs WHERE setting_id=? AND status<>'DELETED'",
                Integer.class, settingId);
    }

    /** 대표 이미지 변경은 작품 잠금 안에서 실행한다. */
    void clearPrimary(long settingId) {
        jdbcTemplate.update("UPDATE character_image_jobs SET is_primary=FALSE WHERE setting_id=? AND is_primary=TRUE", settingId);
    }

    /** 완료된 시안만 대표 이미지로 설정한다. */
    boolean setPrimary(long settingId, String id) {
        return jdbcTemplate.update("""
                UPDATE character_image_jobs SET is_primary=TRUE
                WHERE setting_id=? AND id=? AND status='SUCCEEDED'
                """, settingId, id) == 1;
    }

    /** 삭제 시 이미지와 프롬프트를 지우고 접수 식별 정보만 보존한다. */
    boolean delete(long settingId, String id) {
        return jdbcTemplate.update("""
                UPDATE character_image_jobs
                SET status='DELETED', image_bytes=NULL, mime_type=NULL, prompt='', is_primary=FALSE
                WHERE setting_id=? AND id=? AND status IN ('SUCCEEDED','FAILED')
                """, settingId, id) == 1;
    }

    /** 소유권 검사 이후 해당 인물의 완료된 이미지만 읽는다. */
    Optional<StoredImage> image(long settingId, String id) {
        return jdbcTemplate.query("""
                SELECT mime_type, image_bytes FROM character_image_jobs
                WHERE setting_id=? AND id=? AND status='SUCCEEDED'
                """, (row, index) -> new StoredImage(row.getString("mime_type"), row.getBytes("image_bytes")),
                settingId, id).stream().findFirst();
    }

    /** 실행 중 만료 작업은 중복 과금을 피하도록 실패로 종료한다. */
    void expire(Instant now) {
        jdbcTemplate.update("""
                UPDATE character_image_jobs SET status='FAILED', lease_until=NULL, prompt=''
                WHERE status='RUNNING' AND lease_until < ?
                """, Timestamp.from(now));
    }

    /** 조건부 갱신에 성공한 워커 한 개만 외부 호출을 수행한다. */
    Optional<Pending> claim(Instant leaseUntil) {
        Optional<Pending> next = jdbcTemplate.query("""
                SELECT id, prompt, model FROM character_image_jobs
                WHERE status='QUEUED' ORDER BY created_at, id LIMIT 1
                """, (row, index) -> new Pending(row.getString("id"), row.getString("prompt"), row.getString("model")))
                .stream().findFirst();
        return next.filter(job -> jdbcTemplate.update("""
                UPDATE character_image_jobs SET status='RUNNING', lease_until=?
                WHERE id=? AND status='QUEUED'
                """, Timestamp.from(leaseUntil), job.id()) == 1);
    }

    /** 실행 중인 작업만 완료하며 이미 삭제되거나 만료된 작업을 되살리지 않는다. */
    void finish(String id, StoredImage image) {
        jdbcTemplate.update("""
                UPDATE character_image_jobs SET status=?, mime_type=?, image_bytes=?, lease_until=NULL, prompt=''
                WHERE id=? AND status='RUNNING'
                """, image == null ? "FAILED" : "SUCCEEDED", image == null ? null : image.mimeType(),
                image == null ? null : image.bytes(), id);
    }

    record Receipt(Job job, String fingerprint) {}
    record Pending(String id, String prompt, String model) {}
    record StoredImage(String mimeType, byte[] bytes) {}
}
