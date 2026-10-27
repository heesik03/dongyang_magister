package com.magister.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.magister.backend.domain.analysis.AnalysisPreviewSupport;
import com.magister.backend.domain.analysis.dto.AnalysisPreviewResponse;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.service.AnalysisDispatcher;
import com.magister.backend.domain.analysis.service.AnalysisJobService;
import com.magister.backend.domain.analysis.service.AnalysisJobStateService;
import com.magister.backend.domain.analysis.service.AnalysisPreviewService;
import com.magister.backend.domain.analysis.service.support.AnalysisResultProcessor;
import com.magister.backend.domain.chapter.dto.ChapterResponse;
import com.magister.backend.domain.chapter.dto.CreateChapterRequest;
import com.magister.backend.domain.chapter.dto.UpdateChapterRequest;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.chapter.service.ChapterService;
import com.magister.backend.domain.decision.dto.DecisionRequest;
import com.magister.backend.domain.decision.dto.DecisionResponse;
import com.magister.backend.domain.decision.service.DecisionService;
import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.user.dto.SignupRequest;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.error.UserErrorCode;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.service.UserService;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.entity.Workspace;
import com.magister.backend.domain.workspace.repository.WorkspaceRepository;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 기존 DB를 대상으로 실행하지 않는다. mysqlTest 태스크와 임시 스키마 이름이 모두 필요하다. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(
        named = "MAGISTER_MYSQL_TEST_SCHEMA",
        matches = "magister_refactor_[a-f0-9]{12}")
class MySqlRefactoringTests {
    @Autowired AnalysisPreviewService previewService;
    @Autowired AnalysisJobStateService stateService;
    @Autowired AnalysisResultProcessor resultProcessor;
    @Autowired DecisionService decisionService;
    @Autowired ChapterRepository chapterRepository;
    @Autowired ChapterService chapterService;
    @Autowired UserRepository users;
    @Autowired WorkRepository works;
    @Autowired WorkSettingRepository settings;
    @Autowired WorkspaceRepository workspaces;
    @Autowired AnalysisJobService jobs;
    @Autowired UserService userService;
    @Autowired TransactionTemplate transaction;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AnalysisDispatcher dispatcher;
    @MockitoSpyBean PasswordEncoder passwordEncoder;

    @Test
    void decisionVersionsScopesAndReferencesOnMySql() {
        Fixture fixture = fixture(null);
        chapterService.create(
                fixture.ownerId(),
                fixture.workId(),
                new CreateChapterRequest("c2", "next", "text", "create-next"));
        AnalysisRequest selection = fixture.request();
        AnalysisPreviewResponse preview =
                previewService.preview(fixture.ownerId(), fixture.workId(), selection);
        String jobId =
                jobs.create(
                                fixture.ownerId(),
                                fixture.workId(),
                                selection.confirmed(
                                        preview.previewToken(), preview.policyVersion()))
                        .id();
        stateService.claim(jobId).orElseThrow();
        String provider =
                """
                {"reviews":[{"type":"설정 충돌 후보","quote":"text","startOffset":0,"endOffset":4,"evidences":[{"sourceType":"SETTING","sourceKey":"%s","quote":"evidence","startOffset":0,"endOffset":8,"explanation":"rule"}],"reason":"check","suggestion":"explain"}]}
                """
                        .formatted(fixture.settingId());
        stateService.succeed(jobId, resultProcessor.validateDetailed(provider, preview.input()));
        String reviewId =
                jobs.get(fixture.ownerId(), fixture.workId(), jobId)
                        .result()
                        .reviews()
                        .getFirst()
                        .id();
        DecisionResponse initial =
                decisionService.get(fixture.ownerId(), fixture.workId(), reviewId);
        DecisionRequest request =
                new DecisionRequest(
                        "의도된 설정",
                        "ability change",
                        0,
                        "mysql-decision",
                        initial.basisHash(),
                        false,
                        List.of(
                                new DecisionRequest.Scope(
                                        "CHAPTER_RANGE",
                                        "c1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        "after seal release")));
        DecisionResponse saved =
                decisionService.save(fixture.ownerId(), fixture.workId(), reviewId, request);
        assertThat(saved.version()).isEqualTo(1);
        assertThat(
                        decisionService
                                .save(fixture.ownerId(), fixture.workId(), reviewId, request)
                                .id())
                .isEqualTo(saved.id());
        AnalysisRequest next =
                new AnalysisRequest(
                        "mysql-next",
                        "c2",
                        1,
                        List.of(fixture.settingId().toString()),
                        List.of(),
                        true,
                        true,
                        1L);
        AnalysisPreviewResponse nextPreview =
                previewService.preview(fixture.ownerId(), fixture.workId(), next);
        assertThat(nextPreview.input().decisionReferences()).hasSize(1);
        String nextId =
                jobs.create(
                                fixture.ownerId(),
                                fixture.workId(),
                                next.confirmed(
                                        nextPreview.previewToken(), nextPreview.policyVersion()))
                        .id();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM analysis_decision_references WHERE job_id=?",
                                Long.class,
                                nextId))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM analysis_jobs WHERE id=?", jobId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO"
                                            + " review_decisions(review_id,decision_version,status,reason,mutation_id,request_body,basis_hash,created_at)"
                                            + " SELECT"
                                            + " review_id,decision_version,status,reason,'duplicate-version',request_body,basis_hash,created_at"
                                            + " FROM review_decisions WHERE id=?",
                                        saved.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO"
                                            + " review_decision_scopes(decision_id,scope_type,condition_text)"
                                            + " VALUES(?,'SCENE','invalid')",
                                        saved.id()))
                .isInstanceOfSatisfying(
                        UncategorizedSQLException.class,
                        error ->
                                assertThat(error.getSQLException().getErrorCode()).isEqualTo(3819));
        jobs.cancel(fixture.ownerId(), fixture.workId(), nextId);
    }

    @Test
    void reviewEvidencePersistsAtomicallyAndRejectsOtherJobSourcesOnMySql() {
        Fixture fixture = fixture(null);
        AnalysisRequest request = fixture.request();
        AnalysisPreviewResponse preview =
                previewService.preview(fixture.ownerId(), fixture.workId(), request);
        String jobId =
                jobs.create(
                                fixture.ownerId(),
                                fixture.workId(),
                                request.confirmed(preview.previewToken(), preview.policyVersion()))
                        .id();
        stateService.claim(jobId).orElseThrow();
        String provider =
                """
                {"reviews":[{"type":"설정 충돌 후보","quote":"text","startOffset":0,"endOffset":4,
                "evidences":[{"sourceType":"SETTING","sourceKey":"%s","quote":"evidence","startOffset":0,"endOffset":8,"explanation":"setting"}],
                "reason":"check","suggestion":"explain"}]}
                """
                        .formatted(fixture.settingId());
        stateService.succeed(jobId, resultProcessor.validateDetailed(provider, preview.input()));
        assertThat(jobs.get(fixture.ownerId(), fixture.workId(), jobId).status())
                .isEqualTo("SUCCEEDED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM review_evidences WHERE job_id=?",
                                Long.class,
                                jobId))
                .isEqualTo(1);
        Long targetSource =
                jdbc.queryForObject(
                        "SELECT target_source_id FROM review_candidates WHERE job_id=?",
                        Long.class,
                        jobId);
        String nextId =
                jobs.create(
                                fixture.ownerId(),
                                fixture.workId(),
                                AnalysisPreviewSupport.confirm(
                                        previewService,
                                        fixture.ownerId(),
                                        fixture.workId(),
                                        fixture.request()))
                        .id();
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO"
                                            + " review_candidates(id,job_id,target_source_id,type,quote,start_offset,end_offset,reason,suggestion)"
                                            + " VALUES(?,?,?,?,?,?,?,?,?)",
                                        UUID.randomUUID().toString(),
                                        nextId,
                                        targetSource,
                                        "설정 충돌 후보",
                                        "text",
                                        0,
                                        4,
                                        "check",
                                        "explain"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jobs.cancel(fixture.ownerId(), fixture.workId(), nextId);
        jdbc.update("DELETE FROM analysis_jobs WHERE id=?", jobId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM review_evidences WHERE job_id=?",
                                Long.class,
                                jobId))
                .isZero();
    }

    @Test
    void chapterWritesAreIndependentAndStaleWritesDoNotOverwrite() {
        Fixture fixture = fixture(null);
        ChapterResponse original = chapterService.get(fixture.ownerId(), fixture.workId(), "c1");
        ChapterResponse saved =
                chapterService.update(
                        fixture.ownerId(),
                        fixture.workId(),
                        "c1",
                        new UpdateChapterRequest(
                                "chapter", "saved text", original.version(), "mysql-save"));
        assertThat(saved.version()).isEqualTo(original.version() + 1);
        assertThat(workspaces.findById(fixture.workId()).orElseThrow().getRevision()).isEqualTo(1);
        try {
            chapterService.update(
                    fixture.ownerId(),
                    fixture.workId(),
                    "c1",
                    new UpdateChapterRequest(
                            "chapter", "stale text", original.version(), "mysql-stale"));
            throw new AssertionError("Expected version conflict");
        } catch (CustomException expected) {
            assertThat(expected.getErrorCode().getCode()).isEqualTo("VERSION_CONFLICT");
        }
        assertThat(chapterService.get(fixture.ownerId(), fixture.workId(), "c1").content())
                .isEqualTo("saved text");
    }

    @Test
    void migratesRealMySqlLegacyWorkspaceWithoutChangingReferences() throws Exception {
        String schema =
                "magister_refactor_"
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (!schema.matches("magister_refactor_[a-f0-9]{12}")) {
            throw new IllegalStateException("Invalid verification schema");
        }
        try (Connection connection = jdbc.getDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            String originalSchema = connection.getCatalog();
            statement.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
            try {
                connection.setCatalog(schema);
                SingleConnectionDataSource isolated =
                        new SingleConnectionDataSource(connection, true);
                String location = "classpath:com/magister/backend/global/migration";
                Flyway.configure()
                        .dataSource(isolated)
                        .defaultSchema(schema)
                        .locations(location)
                        .target("8")
                        .load()
                        .migrate();
                // MySQL Statement는 생성 당시 catalog를 기억하므로 스키마를 바꾼 뒤 새로 만든다.
                try (Statement seed = connection.createStatement()) {
                    seed.execute(
                            "INSERT INTO "
                                    + schema
                                    + ".users(id,email,name,password_hash)"
                                    + " VALUES(1,'chapter-migration@example.com','author','hash')");
                    seed.execute(
                            "INSERT INTO "
                                    + schema
                                    + ".works(id,owner_id,title,genre,era,description,created_at,updated_at,version)"
                                    + " VALUES(1,1,'work','fantasy','','',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)");
                }
                WorkspaceData legacy =
                        new WorkspaceData(
                                List.of(
                                        new ChapterData("Case", "chapter 1", "한국어 😀\r\n원고", 9),
                                        new ChapterData("case", "chapter 2", "second", 2)),
                                List.of(),
                                List.of());
                try (PreparedStatement insert =
                        connection.prepareStatement(
                                "INSERT INTO "
                                        + schema
                                        + ".workspaces(work_id,content,revision,version,updated_at)"
                                        + " VALUES(1,?,3,0,CURRENT_TIMESTAMP)")) {
                    insert.setString(1, json.writeValueAsString(legacy));
                    insert.executeUpdate();
                }
                Flyway.configure()
                        .dataSource(isolated)
                        .defaultSchema(schema)
                        .locations(location)
                        .load()
                        .migrate();
                try (ResultSet rows =
                        statement.executeQuery(
                                "SELECT chapter_key,content,revision FROM "
                                        + schema
                                        + ".chapters ORDER BY chapter_no")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("chapter_key")).isEqualTo("Case");
                    assertThat(rows.getString("content")).isEqualTo("한국어 😀\r\n원고");
                    assertThat(rows.getLong("revision")).isEqualTo(9);
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("chapter_key")).isEqualTo("case");
                }
            } finally {
                connection.setCatalog(originalSchema);
                statement.execute("DROP DATABASE " + schema);
            }
        }
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) throws IOException {
        String schema = System.getenv("MAGISTER_MYSQL_TEST_SCHEMA");
        if (schema == null || !schema.matches("magister_refactor_[a-f0-9]{12}")) {
            throw new IllegalStateException("An isolated MySQL verification schema is required");
        }
        Properties configuration = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(".env"))) {
            configuration.load(reader);
        }
        String password = configuration.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));
        if (password == null) {
            throw new IllegalStateException("DB_PASSWORD is missing");
        }

        registry.add("spring.datasource.url", () -> "jdbc:mysql://localhost:3306/" + schema);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> password);
    }

    private record Fixture(Long ownerId, Long workId, Long settingId) {
        AnalysisRequest request() {
            return new AnalysisRequest(
                    UUID.randomUUID().toString(),
                    "c1",
                    1,
                    List.of(settingId.toString()),
                    List.of(),
                    true,
                    true);
        }
    }

    private Fixture fixture(Long existingOwner) {
        Long ownerId =
                existingOwner == null
                        ? users.saveAndFlush(
                                        new User(
                                                UUID.randomUUID() + "@example.com",
                                                "author",
                                                "hash"))
                                .getId()
                        : existingOwner;

        return transaction.execute(
                status -> {
                    Work work =
                            works.saveAndFlush(
                                    new Work(
                                            users.getReferenceById(ownerId),
                                            "work",
                                            "fantasy",
                                            "",
                                            ""));
                    Workspace workspace = new Workspace(work);
                    chapterRepository.saveAndFlush(
                            new Chapter(work, "c1", 1, "chapter", "text", 1, "initial"));
                    workspace.replace(
                            json.writeValueAsString(
                                    new WorkspaceData(List.of(), List.of(), List.of())),
                            "initial");
                    workspaces.saveAndFlush(workspace);
                    Long settingId =
                            settings.saveAndFlush(
                                            new WorkSetting(
                                                    work,
                                                    "rule",
                                                    SettingKind.CHARACTER,
                                                    SettingStatus.CONFIRMED,
                                                    "evidence"))
                                    .getId();
                    return new Fixture(ownerId, work.getId(), settingId);
                });
    }

    @Test
    void concurrentAdmissionsAcrossWorksRespectOwnerLimitOnMySql() throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }
        Fixture first = fixture(null);
        Fixture second = fixture(first.ownerId());
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results =
                    callers.invokeAll(List.of(attempt(first, barrier), attempt(second, barrier)));
            List<Boolean> accepted = new ArrayList<>();
            for (Future<Boolean> result : results) {
                accepted.add(result.get(10, TimeUnit.SECONDS));
            }
            assertThat(accepted).containsExactlyInAnyOrder(true, false);
        }
    }

    private Callable<Boolean> attempt(Fixture fixture, CyclicBarrier barrier) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                jobs.create(
                        fixture.ownerId(),
                        fixture.workId(),
                        AnalysisPreviewSupport.confirm(
                                previewService,
                                fixture.ownerId(),
                                fixture.workId(),
                                fixture.request()));
                return true;
            } catch (CustomException error) {
                assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
                return false;
            }
        };
    }

    @Test
    void simultaneousSignupTranslatesActualMySqlUniqueViolation() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        CyclicBarrier hashing = new CyclicBarrier(2);
        doAnswer(
                        invocation -> {
                            hashing.await(10, TimeUnit.SECONDS);
                            return invocation.callRealMethod();
                        })
                .when(passwordEncoder)
                .encode(anyString());

        Callable<Boolean> signup =
                () -> {
                    try {
                        userService.signup(new SignupRequest(email, "author", "Password1!"));
                        return true;
                    } catch (CustomException error) {
                        assertThat(error.getErrorCode())
                                .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);
                        return false;
                    }
                };
        try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = callers.invokeAll(List.of(signup, signup));
            assertThat(
                            List.of(
                                    results.get(0).get(10, TimeUnit.SECONDS),
                                    results.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void explainCursorUsesExistingWorkKindIndexWithBoundedRows() {
        Fixture target = fixture(null);
        List<Object[]> rows = new ArrayList<>();
        Timestamp now = Timestamp.from(Instant.now());

        transaction.executeWithoutResult(
                status -> {
                    User owner = users.findById(target.ownerId()).orElseThrow();
                    for (int workIndex = 0; workIndex < 21; workIndex++) {
                        Long workId =
                                workIndex == 0
                                        ? target.workId()
                                        : works.saveAndFlush(
                                                        new Work(
                                                                owner,
                                                                "comparison",
                                                                "fantasy",
                                                                "",
                                                                ""))
                                                .getId();
                        for (int settingIndex = 0; settingIndex < 100; settingIndex++) {
                            rows.add(
                                    new Object[] {
                                        workId,
                                        "rule",
                                        settingIndex % 2 == 0 ? "CHARACTER" : "WORLD",
                                        "CONFIRMED",
                                        "evidence",
                                        now,
                                        now,
                                        0
                                    });
                        }
                    }
                    jdbc.batchUpdate(
                            "INSERT INTO"
                                + " work_settings(work_id,title,kind,status,content,created_at,updated_at,version)"
                                + " VALUES (?,?,?,?,?,?,?,?)",
                            rows);
                });
        jdbc.execute("ANALYZE TABLE work_settings");

        String plan =
                jdbc.queryForObject(
                        "EXPLAIN ANALYZE SELECT"
                            + " id,work_id,title,kind,status,content,created_at,updated_at,version"
                            + " FROM work_settings WHERE work_id=? AND kind=? AND id<? ORDER BY id"
                            + " DESC LIMIT 21",
                        String.class,
                        target.workId(),
                        "CHARACTER",
                        Long.MAX_VALUE);
        assertThat(plan).contains("ix_settings_work_kind_id");
        assertThat(plan).contains("rows=21");
        System.out.println("SETTING_CURSOR_EXPLAIN\n" + plan);
    }
}
