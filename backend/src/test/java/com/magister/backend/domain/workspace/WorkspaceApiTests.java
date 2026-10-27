package com.magister.backend.domain.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.analysis.AnalysisPreviewSupport;
import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisJobResponse;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.service.AnalysisJobService;
import com.magister.backend.domain.analysis.service.AnalysisPreviewService;
import com.magister.backend.domain.analysis.service.AnalysisWorker;
import com.magister.backend.domain.analysis.service.support.AnalysisResultProcessor;
import com.magister.backend.domain.chapter.repository.ChapterRepository;
import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.domain.workspace.dto.SaveWorkspaceRequest;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.AnalysisData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ReviewData;
import com.magister.backend.domain.workspace.repository.WorkspaceRepository;
import com.magister.backend.domain.workspace.service.WorkspaceService;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.gemini.GeminiClient;

import jakarta.persistence.EntityManager;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceApiTests {
    @Autowired WorkspaceService workspaceService;
    @Autowired AnalysisPreviewService previewService;
    @Autowired ChapterRepository chapterRepository;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired WorkRepository works;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkSettingRepository settings;
    @Autowired AnalysisJobRepository jobs;
    @Autowired AnalysisJobService jobService;
    @Autowired AnalysisWorker worker;
    @Autowired AnalysisResultProcessor resultValidator;
    @Autowired TransactionTemplate transaction;
    @Autowired EntityManager entityManager;
    @Autowired ObjectMapper json;
    @MockitoBean GeminiClient gemini;
    Long ownerId, workId;
    UsernamePasswordAuthenticationToken owner;

    @BeforeEach
    void prepare() {
        reset(gemini);
        User user =
                users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "검증 작가", "hash"));
        ownerId = user.getId();
        workId = works.saveAndFlush(new Work(user, "저장 검증", "판타지", "", "소개")).getId();
        owner =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(ownerId, user.getEmail(), user.getName()), null, List.of());
    }

    @AfterEach
    void cleanup() {
        transaction.executeWithoutResult(
                status -> {
                    jobs.deleteAll(
                            jobs.findAll().stream()
                                    .filter(job -> job.getWork().getId().equals(workId))
                                    .toList());
                    workspaces.deleteById(workId);
                    settings.deleteAll(
                            settings.findAll().stream()
                                    .filter(item -> item.getWork().getId().equals(workId))
                                    .toList());
                    chapterRepository.deleteAll(
                            chapterRepository.findByWork_IdOrderByChapterNoAsc(workId));
                    chapterRepository.flush();
                    works.deleteById(workId);
                    users.deleteById(ownerId);
                });
    }

    private String path() {
        return "/api/work/" + workId + "/workspace";
    }

    private Map<String, Object> content(String text) {
        return Map.of(
                "chapters",
                List.of(Map.of("id", "chapter-1", "title", "1화", "content", text, "revision", 1)),
                "analyses",
                List.of(),
                "events",
                List.of(
                        Map.of(
                                "id",
                                "event-1",
                                "title",
                                "첫 사건",
                                "time",
                                "1일",
                                "characters",
                                "주인공",
                                "chapterId",
                                "chapter-1",
                                "quote",
                                text)));
    }

    private String body(long version, String mutation, String text) {
        return json.writeValueAsString(
                Map.of("version", version, "mutationId", mutation, "content", content(text)));
    }

    @Test
    void modernEventSavePreservesLegacyReviewsAndRejectsJudgmentRewrite() {
        ReviewData review =
                new ReviewData(
                        "old-review", "설명 보완", "원고", "setting", "근거", "이유", "질문", "보류", "추가 확인");
        AnalysisData analysis =
                new AnalysisData(
                        "legacy",
                        1,
                        "원고",
                        List.of("setting"),
                        List.of(),
                        List.of(review),
                        "2026-10-27T00:00:00Z",
                        null);
        WorkspaceData original =
                new WorkspaceData(
                        List.of(new ChapterData("legacy", "이전 회차", "원고", 1)),
                        List.of(analysis),
                        List.of());
        workspaceService.save(
                ownerId, workId, new SaveWorkspaceRequest(0, "legacy-import", original));
        SaveWorkspaceRequest modern =
                new SaveWorkspaceRequest(1, "event-save", WorkspaceData.empty(), true, true);
        assertThat(workspaceService.save(ownerId, workId, modern).content().analyses())
                .containsExactly(analysis);
        assertThat(workspaceService.save(ownerId, workId, modern).version()).isEqualTo(2);
        assertThatThrownBy(
                        () ->
                                workspaceService.save(
                                        ownerId,
                                        workId,
                                        new SaveWorkspaceRequest(
                                                2,
                                                "erase-judgment",
                                                WorkspaceData.empty(),
                                                true,
                                                false)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(
                        () ->
                                workspaceService.save(
                                        ownerId,
                                        workId,
                                        new SaveWorkspaceRequest(
                                                2,
                                                "modern-with-review",
                                                new WorkspaceData(
                                                        List.of(), List.of(analysis), List.of()),
                                                true,
                                                true)))
                .isInstanceOf(CustomException.class);
        assertThat(workspaceService.get(ownerId, workId).content().analyses())
                .containsExactly(analysis);
    }

    @Test
    void storesDocumentAndRejectsStaleTabWithoutOverwriting() throws Exception {
        mvc.perform(get(path()).with(authentication(owner)))
                .andExpect(jsonPath("$.data.version").value(0));
        String body = body(0, "first-save", "원고 A");
        mvc.perform(
                        put(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.workVersion").value(1));
        mvc.perform(
                        put(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(body))
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(
                        put(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(body(0, "first-save", "원고 B")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(
                        put(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "version",
                                                        1,
                                                        "mutationId",
                                                        "separate-with-chapters",
                                                        "separateChapters",
                                                        true,
                                                        "content",
                                                        content("원고 A")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(
                        put(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(body(0, "other-tab", "원고 B")))
                .andExpect(status().isConflict());
        mvc.perform(get(path()).with(authentication(owner)))
                .andExpect(jsonPath("$.data.content.chapters[0].content").value("원고 A"))
                .andExpect(jsonPath("$.data.content.events[0].title").value("첫 사건"));
    }

    @Test
    void checksOwnershipCsrfAndNestedValidation() throws Exception {
        UsernamePasswordAuthenticationToken other =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(-1L, "other@example.com", "다른 작가"), null, List.of());
        mvc.perform(get(path()).with(authentication(other))).andExpect(status().isNotFound());
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(
                        put(path())
                                .with(authentication(owner))
                                .contentType("application/json")
                                .content(body(0, "x", "내용")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(body(0, "x", "내용").replace("1화", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void linksEntitiesWithoutInitializingParentCollections() {
        transaction.executeWithoutResult(
                status -> {
                    User user = users.findById(ownerId).orElseThrow();
                    assertThat(Hibernate.isInitialized(user.getWorks())).isFalse();
                    Work work = new Work(user, "추가", "판타지", "", "");
                    assertThat(Hibernate.isInitialized(user.getWorks())).isFalse();
                    works.saveAndFlush(work);
                    Work existing = works.findById(workId).orElseThrow();
                    assertThat(Hibernate.isInitialized(existing.getSettings())).isFalse();
                    WorkSetting setting =
                            new WorkSetting(
                                    existing,
                                    "능력",
                                    SettingKind.CHARACTER,
                                    SettingStatus.CONFIRMED,
                                    "마법 불가");
                    assertThat(Hibernate.isInitialized(existing.getSettings())).isFalse();
                    entityManager.detach(existing);
                    Work proxy = entityManager.getReference(Work.class, workId);
                    assertThat(proxy).isNotSameAs(existing);
                    assertThatCode(() -> setting.assignWork(proxy)).doesNotThrowAnyException();
                    status.setRollbackOnly();
                });
    }

    @Test
    void asynchronousJobPublishesValidatedEvidenceAndDeduplicates() throws Exception {
        mvc.perform(
                        put(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(body(0, "analysis-input", "그는 마법을 시전했다.")))
                .andExpect(status().isOk());
        Long settingId =
                transaction.execute(
                        status ->
                                settings.saveAndFlush(
                                                new WorkSetting(
                                                        works.findById(workId).orElseThrow(),
                                                        "능력",
                                                        SettingKind.CHARACTER,
                                                        SettingStatus.CONFIRMED,
                                                        "마법을 사용할 수 없다."))
                                        .getId());
        when(gemini.generateAnalysis(anyString()))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return "{\"reviews\":[{\"type\":\"설정 충돌 후보\",\"quote\":\"그는 마법을"
                                       + " 시전했다.\",\"startOffset\":0,\"endOffset\":12,\"evidences\":[{\"sourceType\":\"SETTING\",\"sourceKey\":\""
                                    + settingId
                                    + "\",\"quote\":\"마법을 사용할 수"
                                    + " 없다.\",\"startOffset\":0,\"endOffset\":13,\"explanation\":\"능력"
                                    + " 근거\"}],\"reason\":\"능력 변화 설명 확인\",\"suggestion\":\"계기를"
                                    + " 보완하세요.\"}]}";
                        });
        AnalysisRequest request =
                new AnalysisRequest(
                        "job-1",
                        "chapter-1",
                        1,
                        List.of(settingId.toString()),
                        List.of(),
                        true,
                        true);
        request = AnalysisPreviewSupport.confirm(previewService, ownerId, workId, request);
        MvcResult accepted =
                mvc.perform(
                                post("/api/work/" + workId + "/analysis")
                                        .with(authentication(owner))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(request)))
                        .andExpect(status().isAccepted())
                        .andReturn();
        String id =
                json.readTree(accepted.getResponse().getContentAsString())
                        .path("data")
                        .path("id")
                        .asString();
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        AnalysisJobResponse result;
        do {
            result = jobService.get(ownerId, workId, id);
            if (result.status().equals("SUCCEEDED") || result.status().equals("FAILED")) break;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.result().reviews().getFirst().evidence()).isEqualTo("마법을 사용할 수 없다.");
        assertThat(jobService.create(ownerId, workId, request).id()).isEqualTo(id);
        verify(gemini, times(1)).generateAnalysis(anyString());
    }

    @Test
    void cancelledQueuedJobDoesNotCallProvider() {
        String id = UUID.randomUUID().toString();
        transaction.executeWithoutResult(
                status ->
                        jobs.saveAndFlush(
                                new AnalysisJob(
                                        id,
                                        works.findById(workId).orElseThrow(),
                                        "cancel-job",
                                        "{}",
                                        "{}")));
        assertThat(jobService.cancel(ownerId, workId, id).status()).isEqualTo("CANCELLED");
        worker.execute(id);
        verifyNoInteractions(gemini);
    }

    @Test
    void rejectsInventedAiEvidence() {
        AnalysisInput input =
                new AnalysisInput(
                        new ChapterData("c", "1화", "실제 원문", 1),
                        List.of(
                                new AnalysisInput.SettingReference(
                                        "1", "능력", "확정 근거", "인물", "확정", 0)),
                        List.of(),
                        List.of(),
                        true,
                        true);
        assertThatThrownBy(
                        () ->
                                resultValidator.validate(
                                        "{\"reviews\":[{\"type\":\"설정 충돌 후보\",\"quote\":\"없는"
                                            + " 원문\",\"settingId\":\"1\",\"reason\":\"이유\",\"suggestion\":\"방향\"}]}",
                                        input))
                .isInstanceOf(CustomException.class);
    }
}
