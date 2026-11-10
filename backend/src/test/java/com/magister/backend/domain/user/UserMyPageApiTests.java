package com.magister.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.repository.AnalysisSourceRepository;
import com.magister.backend.domain.analysis.repository.ReviewCandidateRepository;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.decision.entity.ReviewDecision;
import com.magister.backend.domain.decision.repository.ReviewDecisionRepository;
import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@ActiveProfiles("test")
@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:user-mypage-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class UserMyPageApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkRepository workRepository;
    @Autowired private WorkSettingRepository settingRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private AnalysisJobRepository jobRepository;
    @Autowired private AnalysisSourceRepository sourceRepository;
    @Autowired private ReviewCandidateRepository candidateRepository;
    @Autowired private ReviewDecisionRepository decisionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper json;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM scene_image_jobs");
        jdbcTemplate.update("DELETE FROM scene_boards");
        jdbcTemplate.update("DELETE FROM ai_usage_buckets");
        jdbcTemplate.update("DELETE FROM review_decisions");
        jdbcTemplate.update("DELETE FROM review_candidates");
        jdbcTemplate.update("DELETE FROM analysis_sources");
        jdbcTemplate.update("DELETE FROM analysis_jobs");
        jdbcTemplate.update("DELETE FROM event_extractions");
        jdbcTemplate.update("DELETE FROM workspaces");
        jdbcTemplate.update("DELETE FROM chapters");
        jdbcTemplate.update("DELETE FROM work_settings");
        jdbcTemplate.update("DELETE FROM works");
        jdbcTemplate.update("DELETE FROM users");
    }

    private User createUser(String email, String name, String rawPassword) {
        return userRepository.saveAndFlush(
                new User(email, name, passwordEncoder.encode(rawPassword)));
    }

    private MockHttpSession loginSession(String email, String password) throws Exception {
        MvcResult csrfResult =
                mvc.perform(get("/api/user/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String token =
                json.readTree(csrfResult.getResponse().getContentAsString())
                        .path("data")
                        .path("token")
                        .asString();
        mvc.perform(
                        post("/api/user/login")
                                .session(session)
                                .header("X-CSRF-TOKEN", token)
                                .contentType("application/json")
                                .content(
                                        "{\"email\":\""
                                                + email
                                                + "\",\"password\":\""
                                                + password
                                                + "\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private String fetchCsrfToken(MockHttpSession session) throws Exception {
        MvcResult result =
                mvc.perform(get("/api/user/csrf").session(session))
                        .andExpect(status().isOk())
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("token")
                .asString();
    }

    @Test
    void updatesProfileNameAcrossSessionsAndAllowsDuplicatePenNames() throws Exception {
        createUser("writer1@example.com", "기존필명", "Password1!");
        createUser("writer2@example.com", "공유필명", "Password1!");

        MockHttpSession session1 = loginSession("writer1@example.com", "Password1!");
        MockHttpSession session2 = loginSession("writer1@example.com", "Password1!");
        String token = fetchCsrfToken(session1);

        mvc.perform(
                        patch("/api/user/me/profile")
                                .session(session1)
                                .header("X-CSRF-TOKEN", token)
                                .contentType("application/json")
                                .content("{\"name\":\"  공유필명  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("공유필명"))
                .andExpect(jsonPath("$.data.email").value("writer1@example.com"));

        mvc.perform(get("/api/user/me").session(session1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("공유필명"));
        mvc.perform(get("/api/user/me").session(session2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("공유필명"));

        mvc.perform(
                        patch("/api/user/me/profile")
                                .session(session1)
                                .header("X-CSRF-TOKEN", token)
                                .contentType("application/json")
                                .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void switchingAccountInTheSameSessionIsIsolatedFromThePreviousAccount() throws Exception {
        createUser("first@example.com", "첫 작가", "Password1!");
        createUser("second@example.com", "둘째 작가", "Password1!");
        MockHttpSession sharedBrowser = loginSession("first@example.com", "Password1!");
        MockHttpSession otherBrowser = loginSession("first@example.com", "Password1!");

        mvc.perform(
                        post("/api/user/login")
                                .session(sharedBrowser)
                                .header("X-CSRF-TOKEN", fetchCsrfToken(sharedBrowser))
                                .contentType("application/json")
                                .content(
                                        """
                                        {"email":"second@example.com","password":"Password1!"}
                                        """))
                .andExpect(status().isOk());

        mvc.perform(
                        patch("/api/user/me/profile")
                                .session(otherBrowser)
                                .header("X-CSRF-TOKEN", fetchCsrfToken(otherBrowser))
                                .contentType("application/json")
                                .content("{\"name\":\"변경한 필명\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/user/me").session(sharedBrowser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("second@example.com"))
                .andExpect(jsonPath("$.data.name").value("둘째 작가"));
    }

    @Test
    void aggregatesActivityFromLatestRevisionLatestSucceededAnalysisOnly() throws Exception {
        User writer = createUser("activity@example.com", "작가", "Password1!");
        Work work =
                workRepository.saveAndFlush(
                        new Work(writer, "회귀자의 서재", "판타지", "중세", "설명"));
        Chapter chapter1 =
                chapterRepository.saveAndFlush(
                        new Chapter(work, "ch-1", 1, "1화 시작", "1화 본문", 2L, "mut-1"));
        Chapter chapter2 =
                chapterRepository.saveAndFlush(
                        new Chapter(work, "ch-2", 2, "2화 전개", "2화 본문", 3L, "mut-2"));

        // chapter1: 이전 재분석(SUCCEEDED, revision 2) - 최신 성공 작업에 의해 대체되어야 함
        AnalysisJob olderJob =
                new AnalysisJob(
                        "job-1-old", work, "req-1", "{}", "{\"chapter\":{\"id\":\"ch-1\"}}");
        olderJob.recordConfirmation(chapter1, "v1", "gemini");
        olderJob.start();
        olderJob.succeed("[]");
        jobRepository.saveAndFlush(olderJob);
        AnalysisSource olderTarget =
                sourceRepository.saveAndFlush(
                        new AnalysisSource(
                                olderJob,
                                chapter1,
                                null,
                                "TARGET",
                                "ch-1",
                                2L,
                                "1화 시작",
                                "1화 본문",
                                "{}",
                                "hash-1"));
        candidateRepository.saveAndFlush(
                new ReviewCandidate(
                        UUID.randomUUID().toString(),
                        olderJob,
                        olderTarget,
                        "설정 충돌 후보",
                        "1화",
                        0,
                        2,
                        "이전 분석 사유",
                        "이전 제안"));

        // chapter1: 최신 성공 분석(SUCCEEDED, revision 2) - 후보 2건 중 1건 수용, 1건 미검토
        AnalysisJob latestJob =
                new AnalysisJob(
                        "job-1-latest", work, "req-2", "{}", "{\"chapter\":{\"id\":\"ch-1\"}}");
        latestJob.recordConfirmation(chapter1, "v1", "gemini");
        latestJob.start();
        latestJob.succeed("[]");
        jobRepository.saveAndFlush(latestJob);
        AnalysisSource latestTarget =
                sourceRepository.saveAndFlush(
                        new AnalysisSource(
                                latestJob,
                                chapter1,
                                null,
                                "TARGET",
                                "ch-1",
                                2L,
                                "1화 시작",
                                "1화 본문",
                                "{}",
                                "hash-1"));
        ReviewCandidate unreviewedCandidate =
                candidateRepository.saveAndFlush(
                        new ReviewCandidate(
                                UUID.randomUUID().toString(),
                                latestJob,
                                latestTarget,
                                "설정 충돌 후보",
                                "1화",
                                0,
                                2,
                                "미검토 사유",
                                "미검토 제안"));
        ReviewCandidate acceptedCandidate =
                candidateRepository.saveAndFlush(
                        new ReviewCandidate(
                                UUID.randomUUID().toString(),
                                latestJob,
                                latestTarget,
                                "설명 보완",
                                "본문",
                                3,
                                5,
                                "수용 사유",
                                "수용 제안"));
        decisionRepository.saveAndFlush(
                new ReviewDecision(
                        acceptedCandidate, 1L, "수용", "반영함", "dec-1", "{}", "basis-1"));
        // 미검토 후보는 보류 후 다시 미검토로 되돌린 이력도 미검토로 집계되어야 함
        decisionRepository.saveAndFlush(
                new ReviewDecision(
                        unreviewedCandidate, 1L, "보류", "일단 보류", "dec-2", "{}", "basis-2"));
        decisionRepository.saveAndFlush(
                new ReviewDecision(
                        unreviewedCandidate, 2L, "미검토", "다시 검토", "dec-3", "{}", "basis-2"));

        // chapter2: 이전 원고 버전(revision 2 != 현재 revision 3)에 대한 분석 결과 - 집계 제외되어야 함
        AnalysisJob outdatedRevisionJob =
                new AnalysisJob(
                        "job-2-outdated", work, "req-3", "{}", "{\"chapter\":{\"id\":\"ch-2\"}}");
        outdatedRevisionJob.recordConfirmation(chapter2, "v1", "gemini");
        outdatedRevisionJob.start();
        outdatedRevisionJob.succeed("[]");
        jobRepository.saveAndFlush(outdatedRevisionJob);
        AnalysisSource outdatedTarget =
                sourceRepository.saveAndFlush(
                        new AnalysisSource(
                                outdatedRevisionJob,
                                chapter2,
                                null,
                                "TARGET",
                                "ch-2",
                                2L,
                                "2화 전개",
                                "2화 본문",
                                "{}",
                                "hash-2"));
        candidateRepository.saveAndFlush(
                new ReviewCandidate(
                        UUID.randomUUID().toString(),
                        outdatedRevisionJob,
                        outdatedTarget,
                        "설정 충돌 후보",
                        "2화",
                        0,
                        2,
                        "수정 전 원고 사유",
                        "수정 전 제안"));

        MockHttpSession session = loginSession("activity@example.com", "Password1!");
        mvc.perform(get("/api/user/me/activity").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workCount").value(1))
                .andExpect(jsonPath("$.data.chapterCount").value(2))
                .andExpect(jsonPath("$.data.unreviewedCount").value(1))
                .andExpect(jsonPath("$.data.recentManuscript.workId").value(work.getId()))
                .andExpect(jsonPath("$.data.recentManuscript.chapterId").value("ch-2"))
                .andExpect(jsonPath("$.data.recentManuscript.chapterTitle").value("2화 전개"));
    }

    @Test
    void changesPasswordAndInvalidatesAllActiveSessions() throws Exception {
        createUser("pwd@example.com", "작가", "Password1!");
        MockHttpSession session1 = loginSession("pwd@example.com", "Password1!");
        MockHttpSession session2 = loginSession("pwd@example.com", "Password1!");
        String token = fetchCsrfToken(session1);

        mvc.perform(
                        post("/api/user/me/password")
                                .session(session1)
                                .header("X-CSRF-TOKEN", token)
                                .contentType("application/json")
                                .content(
                                        "{\"currentPassword\":\"WrongPass1!\",\"newPassword\":\"NewPass12!\",\"confirmPassword\":\"NewPass12!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));

        mvc.perform(
                        post("/api/user/me/password")
                                .session(session1)
                                .header("X-CSRF-TOKEN", token)
                                .contentType("application/json")
                                .content(
                                        "{\"currentPassword\":\"Password1!\",\"newPassword\":\"NewPass12!\",\"confirmPassword\":\"Different1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRMATION_MISMATCH"));

        mvc.perform(
                        post("/api/user/me/password")
                                .session(session1)
                                .header("X-CSRF-TOKEN", token)
                                .contentType("application/json")
                                .content(
                                        "{\"currentPassword\":\"Password1!\",\"newPassword\":\"NewPass12!\",\"confirmPassword\":\"NewPass12!\"}"))
                .andExpect(status().isOk());

        assertThat(session1.isInvalid()).isTrue();
        mvc.perform(get("/api/user/me").session(session2)).andExpect(status().isUnauthorized());
        assertThat(session2.isInvalid()).isTrue();

        MockHttpSession reloginSession = loginSession("pwd@example.com", "NewPass12!");
        mvc.perform(get("/api/user/me").session(reloginSession)).andExpect(status().isOk());
    }

    @Test
    void withdrawsAccountCancelsActiveJobsAndDeletesAllOwnedData() throws Exception {
        User writer = createUser("withdraw@example.com", "탈퇴작가", "Password1!");
        Work work =
                workRepository.saveAndFlush(
                        new Work(writer, "삭제될 작품", "판타지", "현대", "설명"));
        settingRepository.saveAndFlush(
                new WorkSetting(
                        work, "주인공", SettingKind.CHARACTER, SettingStatus.CONFIRMED, "상세"));
        Chapter chapter =
                chapterRepository.saveAndFlush(
                        new Chapter(work, "ch-1", 1, "1화", "본문", 1L, "mut-1"));
        AnalysisJob runningJob =
                new AnalysisJob(
                        "job-running", work, "req-run", "{}", "{\"chapter\":{\"id\":\"ch-1\"}}");
        runningJob.recordConfirmation(chapter, "v1", "gemini");
        runningJob.start();
        jobRepository.saveAndFlush(runningJob);

        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO ai_usage_buckets(user_id, feature, period_start, used_count)
                VALUES (?, 'SCENE_ANALYSIS', ?, 1)
                """,
                writer.getId(),
                LocalDate.now());
        jdbcTemplate.update(
                """
                INSERT INTO scene_boards(id, work_id, chapter_key, request_id, selection_json, snapshot_json, status, created_at)
                VALUES ('board-withdraw', ?, 'ch-1', 'req-board', '{}', '{}', 'SUCCEEDED', ?)
                """,
                work.getId(),
                now);
        jdbcTemplate.update(
                """
                INSERT INTO scene_image_jobs(id, board_id, request_id, input_json, status, created_at)
                VALUES ('img-withdraw', 'board-withdraw', 'req-img', '{}', 'QUEUED', ?)
                """,
                now);

        MockHttpSession session1 = loginSession("withdraw@example.com", "Password1!");
        MockHttpSession session2 = loginSession("withdraw@example.com", "Password1!");
        String token = fetchCsrfToken(session1);

        mvc.perform(
                        post("/api/user/me/withdraw")
                                .session(session1)
                                .header("X-CSRF-TOKEN", token)
                                .contentType("application/json")
                                .content(
                                        "{\"currentPassword\":\"Password1!\",\"agreed\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_AGREEMENT_REQUIRED"));

        mvc.perform(
                        post("/api/user/me/withdraw")
                                .session(session1)
                                .header("X-CSRF-TOKEN", token)
                                .contentType("application/json")
                                .content(
                                        "{\"currentPassword\":\"WrongPass1!\",\"agreed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));

        mvc.perform(
                        post("/api/user/me/withdraw")
                                .session(session1)
                                .header("X-CSRF-TOKEN", token)
                                .contentType("application/json")
                                .content(
                                        "{\"currentPassword\":\"Password1!\",\"agreed\":true}"))
                .andExpect(status().isOk());

        assertThat(session1.isInvalid()).isTrue();
        mvc.perform(get("/api/user/me").session(session2)).andExpect(status().isUnauthorized());
        assertThat(session2.isInvalid()).isTrue();
        assertThat(userRepository.existsById(writer.getId())).isFalse();
        assertThat(workRepository.findById(work.getId())).isEmpty();
        assertThat(jobRepository.findById("job-running")).isEmpty();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM ai_usage_buckets WHERE user_id=?",
                                Long.class,
                                writer.getId()))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM scene_boards WHERE id='board-withdraw'",
                                Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM scene_image_jobs WHERE id='img-withdraw'",
                                Long.class))
                .isZero();
    }
}
