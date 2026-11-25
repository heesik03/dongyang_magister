package com.magister.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.service.AnalysisDispatcher;
import com.magister.backend.domain.analysis.service.AnalysisJobService;
import com.magister.backend.domain.analysis.service.AnalysisJobStateService;
import com.magister.backend.domain.analysis.service.support.AnalysisInputFactory;
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
import com.magister.backend.domain.timeline.dto.TimelineData.SaveEventRequest;
import com.magister.backend.domain.timeline.service.LegacyTimelineEventMigrator;
import com.magister.backend.domain.timeline.service.TimelineService;
import com.magister.backend.domain.user.dto.SignupRequest;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.error.UserErrorCode;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.service.UserService;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.domain.work.service.WorkDeletionService;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.entity.Workspace;
import com.magister.backend.domain.workspace.repository.WorkspaceRepository;
import com.magister.backend.domain.workspace.service.WorkspaceSnapshotService;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 기존 DB를 대상으로 실행하지 않는다. mariaDbTest 태스크와 임시 스키마 이름이 모두 필요하다. */
@SpringBootTest(properties = "character-image.poll-delay-ms=3600000")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(
        named = "MAGISTER_MARIADB_TEST_SCHEMA",
        matches = "magister_refactor_[a-f0-9]{12}")
class MariaDbRefactoringTests {
    @Autowired AnalysisInputFactory inputFactory;
    @Autowired WorkspaceSnapshotService workspaceSnapshotService;
    @Autowired AnalysisJobStateService stateService;
    @Autowired AnalysisResultProcessor resultProcessor;
    @Autowired DecisionService decisionService;
    @Autowired WorkDeletionService workDeletionService;
    @Autowired ChapterRepository chapterRepository;
    @Autowired ChapterService chapterService;
    @Autowired UserRepository users;
    @Autowired WorkRepository works;
    @Autowired WorkSettingRepository settings;
    @Autowired WorkspaceRepository workspaces;
    @Autowired LegacyTimelineEventMigrator legacyTimelineEventMigrator;
    @Autowired TimelineService timelineService;
    @Autowired AnalysisJobService jobs;
    @Autowired UserService userService;
    @Autowired TransactionTemplate transaction;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AnalysisDispatcher dispatcher;
    @MockitoSpyBean PasswordEncoder passwordEncoder;

    private AnalysisInput buildInput(Long workId, AnalysisRequest request) {
        return transaction.execute(
                status -> {
                    Work work = works.findById(workId).orElseThrow();
                    return inputFactory.create(
                            workId, request, workspaceSnapshotService.getForOwnedWork(work));
                });
    }

    @Test
    void decisionVersionsScopesReferencesAndWorkDeletionOnMariaDb() {
        Fixture fixture = fixture(null);
        chapterService.create(
                fixture.ownerId(),
                fixture.workId(),
                new CreateChapterRequest("c2", "next", "text", "create-next"));
        AnalysisRequest selection = fixture.request();
        AnalysisInput input = buildInput(fixture.workId(), selection);
        String jobId = jobs.create(fixture.ownerId(), fixture.workId(), selection).id();
        stateService.claim(jobId).orElseThrow();
        String provider =
                """
                {"reviews":[{"type":"설정 충돌 후보","quote":"text","startOffset":0,"endOffset":4,"evidences":[{"sourceType":"SETTING","sourceKey":"%s","quote":"evidence","startOffset":0,"endOffset":8,"explanation":"rule"}],"reason":"check","suggestion":"explain"}]}
                """
                        .formatted(fixture.settingId());
        stateService.succeed(jobId, resultProcessor.validateDetailed(provider, input));
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
                        "mariadb-decision",
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
                                        "after seal release"),
                                new DecisionRequest.Scope(
                                        "SCENE",
                                        null,
                                        null,
                                        jdbc.queryForObject(
                                                "SELECT id FROM analysis_sources WHERE job_id=? AND"
                                                    + " source_type='TARGET'",
                                                Long.class,
                                                jobId),
                                        0,
                                        4,
                                        "specific scene")));
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
                        "mariadb-next",
                        "c2",
                        1,
                        List.of(fixture.settingId().toString()),
                        List.of(),
                        true,
                        true,
                        1L);
        AnalysisInput nextInput = buildInput(fixture.workId(), next);
        assertThat(nextInput.decisionReferences()).hasSize(1);
        String nextId = jobs.create(fixture.ownerId(), fixture.workId(), next).id();
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
                        DataIntegrityViolationException.class,
                        error ->
                                assertThat(error.getMostSpecificCause())
                                        .isInstanceOfSatisfying(SQLException.class, cause ->
                                                assertThat(cause.getErrorCode()).isEqualTo(4025)));
        stateService.claim(nextId).orElseThrow();
        Fixture preserved = fixture(fixture.ownerId());
        workDeletionService.delete(
                fixture.ownerId(),
                fixture.workId(),
                works.findById(fixture.workId()).orElseThrow().getVersion());
        assertThat(works.existsById(fixture.workId())).isFalse();
        assertThat(works.existsById(preserved.workId())).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM analysis_jobs WHERE work_id=?",
                                Long.class,
                                fixture.workId()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM review_decisions WHERE review_id=?",
                                Long.class,
                                reviewId))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM analysis_decision_references WHERE job_id=?",
                                Long.class,
                                nextId))
                .isZero();
        stateService.succeed(
                nextId, resultProcessor.validateDetailed(provider, nextInput));
        assertThat(stateService.claim(nextId)).isEmpty();
        assertThat(stateService.maintain(List.of(nextId))).contains(nextId);
    }

    @Test
    void reviewEvidencePersistsAtomicallyAndRejectsOtherJobSourcesOnMariaDb() {
        Fixture fixture = fixture(null);
        AnalysisRequest request = fixture.request();
        AnalysisInput input = buildInput(fixture.workId(), request);
        String jobId = jobs.create(fixture.ownerId(), fixture.workId(), request).id();
        stateService.claim(jobId).orElseThrow();
        String provider =
                """
                {"reviews":[{"type":"설정 충돌 후보","quote":"text","startOffset":0,"endOffset":4,
                "evidences":[{"sourceType":"SETTING","sourceKey":"%s","quote":"evidence","startOffset":0,"endOffset":8,"explanation":"setting"}],
                "reason":"check","suggestion":"explain"}]}
                """
                        .formatted(fixture.settingId());
        stateService.succeed(jobId, resultProcessor.validateDetailed(provider, input));
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
                                fixture.request())
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
                                "chapter", "saved text", original.version(), "mariadb-save"));
        assertThat(saved.version()).isEqualTo(original.version() + 1);
        assertThat(workspaces.findById(fixture.workId()).orElseThrow().getRevision()).isEqualTo(1);
        try {
            chapterService.update(
                    fixture.ownerId(),
                    fixture.workId(),
                    "c1",
                    new UpdateChapterRequest(
                            "chapter", "stale text", original.version(), "mariadb-stale"));
            throw new AssertionError("Expected version conflict");
        } catch (CustomException expected) {
            assertThat(expected.getErrorCode().getCode()).isEqualTo("VERSION_CONFLICT");
        }
        assertThat(chapterService.get(fixture.ownerId(), fixture.workId(), "c1").content())
                .isEqualTo("saved text");
    }

    private static volatile ScheduledAnnotationBeanPostProcessor runningScheduler;
    private static volatile String createdSchema;
    private static String serverUrl;
    private static String username;
    private static String password;

    @Autowired
    void captureScheduler(ScheduledAnnotationBeanPostProcessor schedulerPostProcessor) {
        runningScheduler = schedulerPostProcessor;
    }

    @DynamicPropertySource
    static void mariaDbProperties(DynamicPropertyRegistry registry) throws Exception {
        String schema = System.getenv("MAGISTER_MARIADB_TEST_SCHEMA");
        if (schema == null || !schema.matches("magister_refactor_[a-f0-9]{12}")) {
            throw new IllegalStateException("An isolated MariaDB verification schema is required");
        }
        Properties configuration = new Properties();
        Path envFile = Path.of(".env");
        if (Files.exists(envFile)) {
            try (Reader reader = Files.newBufferedReader(envFile)) {
                configuration.load(reader);
            }
        }
        String url = requiredSetting(configuration, "DB_URL");
        if (!url.startsWith("jdbc:mariadb://")) {
            throw new IllegalArgumentException("DB_URL must be a MariaDB JDBC URL");
        }
        URI endpoint = URI.create(url.substring("jdbc:".length()));
        if (endpoint.getHost() == null || endpoint.getRawUserInfo() != null
                || endpoint.getRawFragment() != null) {
            throw new IllegalArgumentException("DB_URL must contain a single MariaDB host");
        }
        String baseUrl = "jdbc:mariadb://" + endpoint.getRawAuthority() + "/";
        String options = endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery();
        serverUrl = baseUrl + options;
        username = requiredSetting(configuration, "DB_USERNAME");
        password = requiredSetting(configuration, "DB_PASSWORD");
        try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
                Statement statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MariaDB");
            statement.executeUpdate("CREATE DATABASE " + schema
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            createdSchema = schema;
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        try {
                                            dropIsolatedSchema();
                                        } catch (SQLException ignored) {
                                        }
                                    }));
        }
        registry.add("spring.datasource.url", () -> baseUrl + schema + options);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    private static String requiredSetting(Properties configuration, String key) {
        String value = System.getenv().getOrDefault(key, configuration.getProperty(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required for MariaDB verification");
        }
        return value;
    }

    @AfterAll
    static synchronized void dropIsolatedSchema() throws SQLException {
        ScheduledAnnotationBeanPostProcessor schedulerToStop = runningScheduler;
        if (schedulerToStop != null) {
            runningScheduler = null;
            schedulerToStop.destroy();
        }
        String schemaToDrop = createdSchema;
        if (schemaToDrop != null) {
            try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP DATABASE IF EXISTS " + schemaToDrop);
                createdSchema = null;
            }
        }
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
    void concurrentAdmissionsAcrossWorksRespectOwnerLimitOnMariaDb() throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MariaDB");
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
                        fixture.request());
                return true;
            } catch (CustomException error) {
                assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
                return false;
            }
        };
    }

    @Test
    void simultaneousSignupTranslatesActualMariaDbUniqueViolation() throws Exception {
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

        Map<String, Object> plan =
                jdbc.queryForMap(
                        "ANALYZE SELECT"
                            + " id,work_id,title,kind,status,content,created_at,updated_at,version"
                            + " FROM work_settings WHERE work_id=? AND kind=? AND id<? ORDER BY id"
                            + " DESC LIMIT 21",
                        target.workId(),
                        "CHARACTER",
                        Long.MAX_VALUE);
        assertThat(plan.get("key")).isEqualTo("ix_settings_work_kind_id");
        // MariaDB ANALYZE SELECT의 r_rows 컬럼은 문자열(예: "21.00")로 반환될 수 있다.
        Object actualRowsValue = plan.get("r_rows");
        assertThat(actualRowsValue).isNotNull();
        double actualRows =
                actualRowsValue instanceof Number number
                        ? number.doubleValue()
                        : Double.parseDouble(actualRowsValue.toString());
        assertThat(actualRows).isEqualTo(21.0);
        System.out.println("SETTING_CURSOR_EXPLAIN\n" + plan);
    }

    @Test
    void legacyWorkspaceJsonEventsMigrateIdempotentlyAndIsolateOtherAccountsOnMariaDb() {
        Fixture fixture = fixture(null);
        User otherUser =
                users.saveAndFlush(
                        new User(UUID.randomUUID() + "@example.com", "otherAuthor", "hash"));
        transaction.executeWithoutResult(
                status -> {
                    Workspace workspace = workspaces.findById(fixture.workId()).orElseThrow();
                    List<WorkspaceData.EventData> legacyEvents =
                            List.of(
                                    new WorkspaceData.EventData(
                                            "ev-2",
                                            "두 번째 사건",
                                            "둘째 날",
                                            "주인공",
                                            "c1",
                                            "text",
                                            "설명 2",
                                            1,
                                            "CONFIRMED",
                                            1L,
                                            0,
                                            4,
                                            null),
                                    new WorkspaceData.EventData(
                                            "ev-1",
                                            "첫 번째 사건",
                                            "첫날",
                                            "주인공",
                                            "c1",
                                            "text",
                                            "설명 1",
                                            0,
                                            "CONFIRMED",
                                            1L,
                                            0,
                                            4,
                                            null),
                                    new WorkspaceData.EventData(
                                            "ev-pending",
                                            "미확정 사건",
                                            "",
                                            "주인공",
                                            "c1",
                                            "",
                                            "시점 미확정",
                                            2,
                                            "UNRESOLVED",
                                            null,
                                            null,
                                            null,
                                            null));
                    workspace.clearLegacyEvents(
                            json.writeValueAsString(
                                    new WorkspaceData(List.of(), List.of(), legacyEvents)));
                    workspaces.saveAndFlush(workspace);
                });

        assertThat(legacyTimelineEventMigrator.migrateWorkEvents(fixture.workId())).isTrue();
        List<WorkspaceData.EventData> events =
                timelineService.list(fixture.ownerId(), fixture.workId());
        assertThat(events)
                .extracting(WorkspaceData.EventData::id)
                .containsExactly("ev-1", "ev-2", "ev-pending");
        assertThat(events.get(2).timeStatus()).isEqualTo("UNRESOLVED");

        // 타 계정에서는 해당 작품의 타임라인 조회·수정이 차단된다.
        assertThatThrownBy(() -> timelineService.list(otherUser.getId(), fixture.workId()))
                .isInstanceOf(CustomException.class);

        // 사건 수정 후 재실행해도 중복 생성이나 수정 내용 덮어쓰기가 발생하지 않는다.
        timelineService.update(
                fixture.ownerId(),
                fixture.workId(),
                "ev-1",
                new SaveEventRequest(
                        "ev-1",
                        "수정된 첫 사건",
                        "설명 1",
                        "첫날 아침",
                        "주인공",
                        "c1",
                        "text",
                        0,
                        "CONFIRMED",
                        1L,
                        0,
                        4,
                        null));
        assertThat(legacyTimelineEventMigrator.migrateWorkEvents(fixture.workId())).isFalse();
        List<WorkspaceData.EventData> afterRemigrate =
                timelineService.list(fixture.ownerId(), fixture.workId());
        assertThat(afterRemigrate).hasSize(3);
        assertThat(afterRemigrate.get(0).title()).isEqualTo("수정된 첫 사건");
    }
}
