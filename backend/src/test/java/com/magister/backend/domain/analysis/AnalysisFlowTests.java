package com.magister.backend.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import com.magister.backend.domain.history.service.HistoryCheckService;
import com.magister.backend.domain.history.dto.HistoryCheck;
import com.magister.backend.domain.history.dto.HistoryEvidence;
import com.magister.backend.global.error.code.GeminiErrorCode;
import com.magister.backend.global.error.exception.GeminiApiException;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.analysis.converter.RevisionSuggestionConverter;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.dto.RevisionSuggestion;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.repository.AnalysisSourceRepository;
import com.magister.backend.domain.analysis.service.AnalysisDispatcher;
import com.magister.backend.domain.analysis.service.AnalysisJobService;
import com.magister.backend.domain.analysis.service.AnalysisSnapshotService;
import com.magister.backend.domain.analysis.service.AnalysisWorker;
import com.magister.backend.domain.chapter.dto.CreateChapterRequest;
import com.magister.backend.domain.chapter.dto.UpdateChapterRequest;
import com.magister.backend.domain.chapter.service.ChapterService;
import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.gemini.GeminiClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(
        properties = "spring.datasource.url=jdbc:h2:mem:analysis-flow;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalysisFlowTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired WorkRepository workRepository;
    @Autowired ChapterService chapterService;
    @Autowired WorkSettingRepository settingRepository;
    @Autowired AnalysisJobRepository jobRepository;
    @Autowired AnalysisSourceRepository sourceRepository;
    @Autowired AnalysisJobService jobService;
    @Autowired AnalysisSnapshotService snapshotService;
    @Autowired AnalysisWorker worker;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionTemplate transactionTemplate;
    @MockitoBean HistoryCheckService history;
    @MockitoBean AnalysisDispatcher dispatcher;
    @MockitoBean GeminiClient geminiClient;
    private Long ownerId, workId, settingId;
    private UsernamePasswordAuthenticationToken owner;

    @BeforeEach
    void prepare() {
        User author =
                userRepository.saveAndFlush(
                        new User(UUID.randomUUID() + "@example.com", "검증 작가", "hash"));
        ownerId = author.getId();
        Work work = workRepository.saveAndFlush(new Work(author, "검토 검증", "판타지", "", ""));
        workId = work.getId();
        chapterService.create(
                ownerId,
                workId,
                new CreateChapterRequest("previous", "1화", "이전 근거 원고", "create-1"));
        chapterService.create(
                ownerId, workId, new CreateChapterRequest("target", "2화", "검토 원고", "create-2"));
        settingId =
                transactionTemplate.execute(
                        status ->
                                settingRepository
                                        .saveAndFlush(
                                                new WorkSetting(
                                                        workRepository.getReferenceById(workId),
                                                        "능력",
                                                        SettingKind.CHARACTER,
                                                        SettingStatus.CONFIRMED,
                                                        "마법 불가"))
                                        .getId());
        owner =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(ownerId, author.getEmail(), author.getName()),
                        null,
                        List.of());
    }

    private String path() {
        return "/api/work/" + workId + "/analysis";
    }

    private AnalysisRequest selection() {
        return new AnalysisRequest(
                UUID.randomUUID().toString(),
                "target",
                0,
                List.of(settingId.toString()),
                List.of("previous"),
                true,
                true,
                1L);
    }

    @Test
    void historyOnlyUsesSnapshotAndReplaysStoredEvidence() throws Exception {
        String manuscript = "1392년, 사신은 경복궁에 도착했다.";
        chapterService.update(ownerId, workId, "target", new UpdateChapterRequest("2화", manuscript, 0, "1392"));
        AnalysisRequest request = new AnalysisRequest("history", "target", 0, List.of(), List.of(), false, false, 2L, true);
        String jobId = jobService.create(ownerId, workId, request).id();
        chapterService.update(ownerId, workId, "target", new UpdateChapterRequest("2화",
                manuscript.replace("1392", "1396"), 1, "1396"));
        HistoryEvidence evidence = new HistoryEvidence("국가유산청", "palace", "경복궁",
                URI.create("https://www.heritage.go.kr/"), HistoryEvidence.Status.FOUND, "1395년에 창건했다.");
        when(history.checkJob(ownerId, manuscript)).thenReturn(List.of(new HistoryCheck.Finding(
                manuscript, 0, manuscript.length(), "1392년", "경복궁", "경복궁이 존재했다",
                HistoryCheck.Status.POSSIBLE_CONFLICT, "창건 전", List.of(evidence))));
        worker.execute(jobId);
        worker.execute(jobId);
        assertThat(jobService.create(ownerId, workId, request).id()).isEqualTo(jobId);
        mockMvc.perform(get(path() + "/" + jobId + "/review").with(authentication(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stale").value(true))
                .andExpect(jsonPath("$.data.history.revision").value(2))
                .andExpect(jsonPath("$.data.history.findings[0].quote").value(manuscript))
                .andExpect(jsonPath("$.data.history.findings[0].evidence[0].passage").value("1395년에 창건했다."));
        String revised = manuscript.replace("1392", "1396");
        AnalysisRequest next = new AnalysisRequest("history-revised", "target", 0, List.of(), List.of(),
                false, false, 3L, true);
        when(history.checkJob(ownerId, revised)).thenReturn(List.of(new HistoryCheck.Finding(
                revised, 0, revised.length(), "1396년", "경복궁", "경복궁이 존재했다",
                HistoryCheck.Status.NO_CONFLICT_FOUND, "창건 이후", List.of(evidence))));
        String nextId = jobService.create(ownerId, workId, next).id();
        worker.execute(nextId);
        mockMvc.perform(get(path() + "/" + nextId + "/review").with(authentication(owner)))
                .andExpect(jsonPath("$.data.stale").value(false))
                .andExpect(jsonPath("$.data.history.findings[0].status").value("NO_CONFLICT_FOUND"));
        verify(history).checkJob(ownerId, manuscript);
        verifyNoInteractions(geminiClient);
    }

    @Test
    void historyFailurePreservesStandardFindings() throws Exception {
        AnalysisRequest request = new AnalysisRequest("mixed", "target", 0, List.of(settingId.toString()),
                List.of("previous"), true, true, 1L, true);
        String jobId = jobService.create(ownerId, workId, request).id();
        when(geminiClient.generateAnalysis(anyString())).thenReturn(linkedProviderResult());
        when(history.checkJob(ownerId, "검토 원고")).thenThrow(new GeminiApiException(GeminiErrorCode.UNAVAILABLE));
        worker.execute(jobId);
        mockMvc.perform(get(path() + "/" + jobId + "/review").with(authentication(owner)))
                .andExpect(jsonPath("$.data.history.historicalStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.history.standardStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.candidates.totalElements").value(1));
    }

    @Test
    void refusesOversizeHistoricalManuscriptBeforeDispatch() throws Exception {
        chapterService.update(ownerId, workId, "target", new UpdateChapterRequest(
                "2화", "가".repeat(6001), 0, "long-chapter"));
        AnalysisRequest request = new AnalysisRequest("long", "target", 0, List.of(), List.of(),
                false, false, 2L, true);
        mockMvc.perform(post(path()).with(authentication(owner)).with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HISTORY_SIZE_LIMIT"));
        verifyNoInteractions(dispatcher, history, geminiClient);
    }

    private AnalysisRequest confirmed() {
        return selection();
    }

    private String linkedProviderResult() {
        return """
        {"reviews":[{"type":"설정 충돌 후보","quote":"검토 원고","startOffset":0,"endOffset":5,
        "evidences":[{"sourceType":"SETTING","sourceKey":"%s","quote":"마법 불가","startOffset":0,"endOffset":5,"explanation":"능력 설정"},
        {"sourceType":"PREVIOUS","sourceKey":"previous","quote":"근거 원고","startOffset":3,"endOffset":8,"explanation":"이전 행동"}],
        "reason":"계기 확인","suggestion":"설명 보완"}]}
        """
                .formatted(settingId);
    }

    @Test
    void equivalentActiveRequestAndItsReplayReuseOneJob() {
        AnalysisRequest firstRequest = confirmed();
        AnalysisRequest secondRequest = confirmed();
        String jobId = jobService.create(ownerId, workId, firstRequest).id();

        assertThat(jobService.create(ownerId, workId, secondRequest).id()).isEqualTo(jobId);
        when(geminiClient.generateAnalysis(anyString())).thenReturn("{\"reviews\":[]}");
        worker.execute(jobId);
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus())
                .isEqualTo(AnalysisJobStatus.SUCCEEDED);

        assertThat(jobService.create(ownerId, workId, secondRequest).id()).isEqualTo(jobId);
        assertThat(jobService.create(ownerId, workId, confirmed()).id()).isNotEqualTo(jobId);
    }

    @Test
    void legacyResultRemainsReadableWithoutInventingLocations() throws Exception {
        String jobId = jobService.create(ownerId, workId, confirmed()).id();
        transactionTemplate.executeWithoutResult(
                transaction -> {
                    AnalysisJob job = jobRepository.findById(jobId).orElseThrow();
                    job.start();
                    job.succeed(
                            """
                            {"chapterId":"target","revision":1,"source":"검토 원고","settingIds":["%s"],
                             "previousIds":["previous"],"reviews":[],"createdAt":"2026-10-26T00:00:00Z"}
                            """
                                    .formatted(settingId));
                });
        mockMvc.perform(get(path() + "/" + jobId + "/review").with(authentication(owner)))
                .andExpect(jsonPath("$.data.legacy").value(true))
                .andExpect(jsonPath("$.data.candidates.totalElements").value(0));
    }

    @Test
    void linkedReviewsSourcesAccessAndStaleStateAreRestored() throws Exception {
        String jobId = jobService.create(ownerId, workId, confirmed()).id();
        when(geminiClient.generateAnalysis(anyString())).thenReturn(linkedProviderResult());
        worker.execute(jobId);
        mockMvc.perform(get(path() + "/" + jobId + "/review").with(authentication(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legacy").value(false))
                .andExpect(jsonPath("$.data.stale").value(false))
                .andExpect(jsonPath("$.data.candidates.totalElements").value(1))
                .andExpect(jsonPath("$.data.candidates.items[0].startOffset").value(0))
                .andExpect(jsonPath("$.data.candidates.items[0].evidences.length()").value(2))
                .andExpect(
                        jsonPath("$.data.candidates.items[0].evidences[1].sourceType")
                                .value("PREVIOUS"));
        AnalysisSource targetSource =
                sourceRepository.findByJob_IdOrderByIdAsc(jobId).getFirst();
        assertThat(targetSource.getSourceKey()).isEqualTo("target");
        assertThat(targetSource.getContent()).isEqualTo("검토 원고");
        chapterService.update(
                ownerId,
                workId,
                "previous",
                new UpdateChapterRequest("1화", "바뀐 이전 원고", 0, "edit-prev"));
        mockMvc.perform(get(path() + "/" + jobId + "/review").with(authentication(owner)))
                .andExpect(jsonPath("$.data.stale").value(true));
        assertThat(
                        sourceRepository
                                .findByJob_IdOrderByIdAsc(jobId)
                                .getFirst()
                                .getContent())
                .isEqualTo("검토 원고");
        mockMvc.perform(
                        get(path() + "/" + jobId + "/review")
                                .with(authentication(owner))
                                .param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path() + "/" + jobId + "/review")).andExpect(status().isUnauthorized());
        UsernamePasswordAuthenticationToken other =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(-1L, "other@example.com", "타인"), null, List.of());
        mockMvc.perform(get(path() + "/" + jobId + "/review").with(authentication(other)))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"설정 충돌 후보", "설명 보완"})
    void suggestionUsesOnlyConfirmedContextAndRejectsStaleOrForeignRequests(String candidateType) throws Exception {
        String jobId = jobService.create(ownerId, workId, confirmed()).id();
        when(geminiClient.generateAnalysis(anyString())).thenReturn(linkedProviderResult().replace("설정 충돌 후보", candidateType));
        worker.execute(jobId);
        String id = jdbcTemplate.queryForObject(
                "select id from review_candidates where job_id = ?", String.class, jobId);
        String url = "/api/work/" + workId + "/review/" + id + "/revision-suggestion";
        String preview = mockMvc.perform(get(url + "/context").with(authentication(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.material.settings.length()").value(1))
                .andExpect(jsonPath("$.data.material.settings[0].quote").value("마법 불가"))
                .andExpect(jsonPath("$.data.material.previous.length()").value(1))
                .andExpect(jsonPath("$.data.material.previous[0].title").value("1화"))
                .andExpect(jsonPath("$.data.material.previous[0].quote").value("근거 원고"))
                .andExpect(jsonPath("$.data.material.previous[0].explanation").value("이전 행동"))
                .andExpect(jsonPath("$.data.target.chapterId").value("target"))
                .andReturn().getResponse().getContentAsString();
        String hash = objectMapper.readTree(preview).at("/data/contextHash").asString();
        String body = objectMapper.writeValueAsString(Map.of("contextHash", hash));
        UsernamePasswordAuthenticationToken other = UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(-1L, "other@example.com", "타인"), null, List.of());
        mockMvc.perform(get(url + "/context").with(authentication(other))).andExpect(status().isNotFound());
        mockMvc.perform(post(url).with(authentication(owner)).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(authentication(owner)).with(csrf()).contentType("application/json")
                .content(body.replace(hash, "0".repeat(64)))).andExpect(status().isConflict());
        when(geminiClient.generateText(anyString(), eq(1024))).thenReturn("보완 원고");
        // 방금 실행한 분석과 같은 사용자 슬롯을 사용하므로 429가 해제된 첫 응답을 검증한다.
        MockHttpServletResponse generated = await().atMost(Duration.ofSeconds(12))
                .pollInterval(Duration.ofMillis(250))
                .until(() -> mockMvc.perform(post(url).with(authentication(owner)).with(csrf())
                                .contentType("application/json").content(body)).andReturn().getResponse(),
                        response -> response.getStatus() != 429);
        assertThat(generated.getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(generated.getContentAsByteArray()).at("/data/replacement").asString())
                .isEqualTo("보완 원고");
        String chapterUrl = "/api/work/" + workId + "/chapter/target";
        mockMvc.perform(get(chapterUrl).with(authentication(owner)))
                .andExpect(jsonPath("$.data.content").value("검토 원고"));
        String edit = objectMapper.writeValueAsString(new UpdateChapterRequest("2화", "보완 원고", 0, "apply-target"));
        mockMvc.perform(put(chapterUrl).with(authentication(owner)).with(csrf())
                        .contentType("application/json").content(edit))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(2));
        mockMvc.perform(get(chapterUrl).with(authentication(owner)))
                .andExpect(jsonPath("$.data.content").value("보완 원고"));
        mockMvc.perform(post(url).with(authentication(owner)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void suggestionPreservesQuoteBoundaryWhitespaceAndStripsCodeFence() {
        RevisionSuggestionConverter converter = new RevisionSuggestionConverter(objectMapper);
        RevisionSuggestion.Target target =
                new RevisionSuggestion.Target("target", 1L, 0, 10, "  고개를 들었다.\n");

        RevisionSuggestion.Result result =
                converter.toResult(target, "```text\n\"천천히 고개를 들었다.\"\n```");
        assertThat(result.replacement()).isEqualTo("  \"천천히 고개를 들었다.\"\n");

        assertThatThrownBy(() -> converter.toResult(target, "  고개를 들었다.  "))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> converter.toResult(target, "설명\n```\n코드\n```"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void anotherJobSourceIsRejectedByCompositeForeignKey() {
        String firstId = jobService.create(ownerId, workId, confirmed()).id();
        when(geminiClient.generateAnalysis(anyString())).thenReturn(linkedProviderResult());
        worker.execute(firstId);
        String secondId = jobService.create(ownerId, workId, confirmed()).id();
        Long firstSource = sourceRepository.findByJob_IdOrderByIdAsc(firstId).getFirst().getId();
        assertThat(sourceRepository.findByIdAndJob_Id(firstSource, secondId)).isEmpty();
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO"
                                            + " review_candidates(id,job_id,target_source_id,type,quote,start_offset,end_offset,reason,suggestion)"
                                            + " VALUES(?,?,?,?,?,?,?,?,?)",
                                        UUID.randomUUID().toString(),
                                        secondId,
                                        firstSource,
                                        "설정 충돌 후보",
                                        "검토 원고",
                                        0,
                                        5,
                                        "이유",
                                        "방향"))
                .isInstanceOf(DataIntegrityViolationException.class);
        worker.execute(secondId);
        String candidateId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM review_candidates WHERE job_id=?", String.class, firstId);
        Long secondSource = sourceRepository.findByJob_IdOrderByIdAsc(secondId).getLast().getId();
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO"
                                            + " review_evidences(job_id,review_id,source_id,quote,start_offset,end_offset,explanation)"
                                            + " VALUES(?,?,?,?,?,?,?)",
                                        firstId,
                                        candidateId,
                                        secondSource,
                                        "마법 불가",
                                        0,
                                        5,
                                        "이유"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbcTemplate.update("DELETE FROM analysis_jobs WHERE id=?", firstId);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM review_candidates WHERE job_id=?",
                                Long.class,
                                firstId))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM review_evidences WHERE job_id=?",
                                Long.class,
                                firstId))
                .isZero();
    }

    @Test
    void invalidAndCancelledJobsLeaveNoPartialReviews() {
        String invalidId = jobService.create(ownerId, workId, confirmed()).id();
        when(geminiClient.generateAnalysis(anyString()))
                .thenReturn(linkedProviderResult().replace("\"endOffset\":5", "\"endOffset\":4"));
        worker.execute(invalidId);
        assertThat(jobService.get(ownerId, workId, invalidId).status()).isEqualTo("FAILED");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM review_candidates WHERE job_id=?",
                                Long.class,
                                invalidId))
                .isZero();
        String cancelledId = jobService.create(ownerId, workId, confirmed()).id();
        jobService.cancel(ownerId, workId, cancelledId);
        worker.execute(cancelledId);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM review_evidences WHERE job_id=?",
                                Long.class,
                                cancelledId))
                .isZero();
    }

    @Test
    void rejectsIdeaSettingAndChecksAccess() throws Exception {
        transactionTemplate.executeWithoutResult(
                status ->
                        settingRepository
                                .findById(settingId)
                                .orElseThrow()
                                .update("능력", SettingKind.CHARACTER, SettingStatus.IDEA, "아이디어"));
        mockMvc.perform(
                        post(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(selection())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(geminiClient, dispatcher);
    }

    @Test
    void expiryCleanupCascadesSourcesAndEmptyForeignHistoryIsNotReadable() throws Exception {
        String jobId = jobService.create(ownerId, workId, confirmed()).id();
        assertThat(sourceRepository.findByJob_IdOrderByIdAsc(jobId)).hasSize(3);
        jobRepository.deleteAllByIdInBatch(List.of(jobId));
        assertThat(sourceRepository.findByJob_IdOrderByIdAsc(jobId)).isEmpty();
        UsernamePasswordAuthenticationToken other =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(-1L, "other@example.com", "타인"), null, List.of());
        mockMvc.perform(get(path()).with(authentication(other))).andExpect(status().isNotFound());
    }

    @Test
    void snapshotsSurviveEditingAndReplayDoesNotCreateNewSources() {
        AnalysisRequest request = confirmed();
        String jobId = jobService.create(ownerId, workId, request).id();
        List<AnalysisSource> sources = sourceRepository.findByJob_IdOrderByIdAsc(jobId);
        assertThat(sources).hasSize(3);
        assertThat(sources.stream().map(AnalysisSource::getType))
                .containsExactly("TARGET", "PREVIOUS", "SETTING");
        chapterService.update(
                ownerId,
                workId,
                "target",
                new UpdateChapterRequest("2화", "새 원고", 0, "edit-target"));
        assertThat(jobService.create(ownerId, workId, request).id()).isEqualTo(jobId);
        List<AnalysisSource> persistedSources = sourceRepository.findByJob_IdOrderByIdAsc(jobId);
        assertThat(persistedSources).hasSize(3);
        assertThat(persistedSources.getFirst().getContent()).isEqualTo("검토 원고");
        AnalysisJob job = jobRepository.findById(jobId).orElseThrow();
        assertThat(snapshotService.isStale(workId, job.getInput())).isTrue();
    }

    @Test
    void historyAndCompletedResultCanBeRecoveredWithoutClientSaving() throws Exception {
        String jobId = jobService.create(ownerId, workId, confirmed()).id();
        when(geminiClient.generateAnalysis(anyString()))
                .thenReturn(
                        "{\"reviews\":[{\"type\":\"설정 충돌 후보\",\"quote\":\"검토"
                            + " 원고\",\"startOffset\":0,\"endOffset\":5,\"evidences\":[{\"sourceType\":\"SETTING\",\"sourceKey\":\""
                                + settingId
                                + "\",\"quote\":\"마법"
                                + " 불가\",\"startOffset\":0,\"endOffset\":5,\"explanation\":\"관련"
                                + " 근거\"}],\"reason\":\"확인\",\"suggestion\":\"보완\"}]}");
        worker.execute(jobId);
        mockMvc.perform(
                        get(path())
                                .with(authentication(owner))
                                .param("chapterId", "target")
                                .param("status", "SUCCEEDED"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(jobId))
                .andExpect(jsonPath("$.data.items[0].input").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].result").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].previewConfirmed").value(true));
        mockMvc.perform(get(path() + "/" + jobId).with(authentication(owner)))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.reviews[0].quote").value("검토 원고"));
        mockMvc.perform(get(path()).with(authentication(owner)).param("chapterId", "previous"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get(path()).with(authentication(owner)).param("size", "101"))
                .andExpect(status().isBadRequest());
        UsernamePasswordAuthenticationToken other =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(-1L, "other@example.com", "타인"), null, List.of());
        mockMvc.perform(get(path() + "/" + jobId).with(authentication(other)))
                .andExpect(status().isNotFound());
    }
}
