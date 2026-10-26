package com.magister.backend.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.repository.AnalysisSourceRepository;
import com.magister.backend.domain.analysis.service.AnalysisDispatcher;
import com.magister.backend.domain.analysis.service.AnalysisJobService;
import com.magister.backend.domain.analysis.service.AnalysisPreviewService;
import com.magister.backend.domain.analysis.service.AnalysisWorker;
import com.magister.backend.domain.analysis.service.support.AnalysisPreviewToken;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
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
    @Autowired AnalysisPreviewService previewService;
    @Autowired AnalysisJobService jobService;
    @Autowired AnalysisWorker worker;
    @Autowired TransactionTemplate transactionTemplate;
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

    private AnalysisRequest confirmed() {
        return AnalysisPreviewSupport.confirm(previewService, ownerId, workId, selection());
    }

    @Test
    void previewDoesNotCreateJobOrCallProviderAndChecksAccess() throws Exception {
        mockMvc.perform(
                        post(path() + "/preview")
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(selection())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.input.chapter.content").value("검토 원고"))
                .andExpect(jsonPath("$.data.input.previous[0].content").value("이전 근거 원고"))
                .andExpect(jsonPath("$.data.input.settings[0].content").value("마법 불가"))
                .andExpect(jsonPath("$.data.previewToken").isString())
                .andExpect(jsonPath("$.data.policyVersion").isString());
        assertThat(jobRepository.findHistory(workId, ownerId, null, null, PageRequest.of(0, 10)))
                .isEmpty();
        verifyNoInteractions(geminiClient, dispatcher);
        mockMvc.perform(
                        post(path() + "/preview")
                                .with(authentication(owner))
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(selection())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(path())).andExpect(status().isUnauthorized());
        UsernamePasswordAuthenticationToken other =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(-1L, "other@example.com", "타인"), null, List.of());
        mockMvc.perform(
                        post(path() + "/preview")
                                .with(authentication(other))
                                .with(csrf())
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(selection())))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsMissingConsentAndChangedPreviousManuscript() throws Exception {
        mockMvc.perform(
                        post(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(selection())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PREVIEW_REQUIRED"));
        AnalysisRequest request = confirmed();
        chapterService.update(
                ownerId,
                workId,
                "previous",
                new UpdateChapterRequest("1화", "바뀐 이전 원고", 0, "edit-reference"));
        mockMvc.perform(
                        post(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIEW_CHANGED"));
        verifyNoInteractions(geminiClient, dispatcher);
    }

    @Test
    void changingConfirmedSettingInvalidatesPreviewAndIdeaIsRejected() throws Exception {
        AnalysisRequest request = confirmed();
        transactionTemplate.executeWithoutResult(
                status ->
                        settingRepository
                                .findById(settingId)
                                .orElseThrow()
                                .update(
                                        "능력",
                                        SettingKind.CHARACTER,
                                        SettingStatus.CONFIRMED,
                                        "바뀐 확정 설정"));
        mockMvc.perform(
                        post(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
        transactionTemplate.executeWithoutResult(
                status ->
                        settingRepository
                                .findById(settingId)
                                .orElseThrow()
                                .update("능력", SettingKind.CHARACTER, SettingStatus.IDEA, "아이디어"));
        mockMvc.perform(
                        post(path() + "/preview")
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
    void snapshotsSurviveEditingAndReplayDoesNotCreateNewSources() throws Exception {
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
        assertThat(sourceRepository.findByJob_IdOrderByIdAsc(jobId)).hasSize(3);
        mockMvc.perform(get(path() + "/" + jobId + "/snapshot").with(authentication(owner)))
                .andExpect(jsonPath("$.data.input.chapter.content").value("검토 원고"))
                .andExpect(jsonPath("$.data.sources[0].content").value("검토 원고"))
                .andExpect(jsonPath("$.data.stale").value(true))
                .andExpect(jsonPath("$.data.legacy").value(false));
    }

    @Test
    void historyAndCompletedResultCanBeRecoveredWithoutClientSaving() throws Exception {
        String jobId = jobService.create(ownerId, workId, confirmed()).id();
        when(geminiClient.generateText(anyString(), eq(4096)))
                .thenReturn(
                        "{\"reviews\":[{\"type\":\"설정 충돌 후보\",\"quote\":\"검토 원고\",\"settingId\":\""
                                + settingId
                                + "\",\"reason\":\"확인\",\"suggestion\":\"보완\"}]}");
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
        mockMvc.perform(get(path() + "/" + jobId + "/snapshot").with(authentication(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void expiredTamperedAndOtherContextTokensAreRejected() {
        AnalysisPreviewToken token = new AnalysisPreviewToken("01234567890123456789012345678901");
        String expired = token.issue("user:work:input", Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> token.verify(expired, "user:work:input"))
                .isInstanceOf(CustomException.class);
        String valid = token.issue("user:work:input", Instant.now().plusSeconds(60));
        assertThatThrownBy(() -> token.verify(valid, "other:work:input"))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> token.verify(valid + "x", "user:work:input"))
                .isInstanceOf(CustomException.class);
    }
}
