package com.magister.backend.domain.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.analysis.dto.AnalysisOutput;
import com.magister.backend.domain.analysis.dto.AnalysisPreviewResponse;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.service.AnalysisDispatcher;
import com.magister.backend.domain.analysis.service.AnalysisJobService;
import com.magister.backend.domain.analysis.service.AnalysisJobStateService;
import com.magister.backend.domain.analysis.service.AnalysisPreviewService;
import com.magister.backend.domain.analysis.service.AnalysisSnapshotService;
import com.magister.backend.domain.analysis.service.support.AnalysisResultProcessor;
import com.magister.backend.domain.chapter.dto.CreateChapterRequest;
import com.magister.backend.domain.chapter.service.ChapterService;
import com.magister.backend.domain.decision.dto.DecisionRequest;
import com.magister.backend.domain.decision.dto.DecisionResponse;
import com.magister.backend.domain.decision.error.DecisionErrorCode;
import com.magister.backend.domain.decision.service.DecisionService;
import com.magister.backend.domain.setting.dto.CreateSettingRequest;
import com.magister.backend.domain.setting.dto.UpdateSettingRequest;
import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.service.SettingService;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.pagination.OffsetPageRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SpringBootTest(
        properties = "spring.datasource.url=jdbc:h2:mem:decision-flow;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DecisionFlowTests {
    @Autowired DecisionService decisionService;
    @Autowired ChapterService chapterService;
    @Autowired SettingService settingService;
    @Autowired AnalysisPreviewService previewService;
    @Autowired AnalysisJobService jobService;
    @Autowired AnalysisJobStateService stateService;
    @Autowired AnalysisSnapshotService snapshotService;
    @Autowired AnalysisResultProcessor resultProcessor;
    @Autowired AnalysisJobRepository jobRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkRepository workRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AnalysisDispatcher dispatcher;
    private Long ownerId, workId, settingId;
    private String jobId, reviewId;
    private UsernamePasswordAuthenticationToken owner;

    @BeforeEach
    void setup() {
        User author =
                userRepository.saveAndFlush(
                        new User(UUID.randomUUID() + "@example.com", "작가", "hash"));
        ownerId = author.getId();
        workId = workRepository.saveAndFlush(new Work(author, "작품", "판타지", "", "")).getId();
        for (int number = 1; number <= 4; number++) {
            chapterService.create(
                    ownerId,
                    workId,
                    new CreateChapterRequest(
                            "c" + number, number + "화", "마법 사용", "create-" + number));
        }
        settingId =
                settingService
                        .create(
                                ownerId,
                                workId,
                                new CreateSettingRequest(
                                        "능력",
                                        SettingKind.CHARACTER,
                                        SettingStatus.CONFIRMED,
                                        "마법 불가"))
                        .id();
        AnalysisRequest request = request("c2", List.of(settingId.toString()));
        AnalysisPreviewResponse preview = previewService.preview(ownerId, workId, request);
        jobId =
                jobService
                        .create(
                                ownerId,
                                workId,
                                request.confirmed(preview.previewToken(), preview.policyVersion()))
                        .id();
        stateService.claim(jobId).orElseThrow();
        stateService.succeed(jobId, resultProcessor.validateDetailed(provider(), preview.input()));
        reviewId = jobService.get(ownerId, workId, jobId).result().reviews().getFirst().id();
        owner =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(ownerId, author.getEmail(), author.getName()),
                        null,
                        List.of());
    }

    private AnalysisRequest request(String chapter, List<String> settings) {
        return new AnalysisRequest(
                UUID.randomUUID().toString(), chapter, 0, settings, List.of(), true, true, 1L);
    }

    private AnalysisPreviewResponse preview(String chapter) {
        return previewService.preview(
                ownerId, workId, request(chapter, List.of(settingId.toString())));
    }

    private String provider() {
        return """
        {"reviews":[{"type":"설정 충돌 후보","quote":"마법 사용","startOffset":0,"endOffset":5,
        "evidences":[{"sourceType":"SETTING","sourceKey":"%s","quote":"마법 불가","startOffset":0,"endOffset":5,"explanation":"능력 제약"}],"reason":"계기 확인","suggestion":"설명 보완"}]}
        """
                .formatted(settingId);
    }

    private String path() {
        return "/api/work/" + workId + "/review/" + reviewId + "/decision";
    }

    private DecisionRequest.Scope range(String from, String to) {
        return new DecisionRequest.Scope(
                "CHAPTER_RANGE", from, to, null, null, null, "봉인 해제 이후 주인공 능력에만 적용");
    }

    private DecisionRequest intent(long version, List<DecisionRequest.Scope> scopes) {
        return new DecisionRequest(
                "의도된 설정",
                "의도한 능력 변화",
                version,
                UUID.randomUUID().toString(),
                decisionService.get(ownerId, workId, reviewId).basisHash(),
                false,
                scopes);
    }

    private DecisionResponse save(DecisionRequest request) {
        return decisionService.save(ownerId, workId, reviewId, request);
    }

    @Test
    void versionsIdempotencyWithdrawalHistoryAndAccessAreExplicit() throws Exception {
        DecisionRequest request = intent(0, List.of(range("c2", "c3")));
        mockMvc.perform(
                        put(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
        assertThat(save(request).version()).isEqualTo(1);
        DecisionResponse initial = decisionService.get(ownerId, workId, reviewId);
        assertThatThrownBy(() -> save(intent(0, List.of(range("c2", "c3")))))
                .isInstanceOf(CustomException.class);
        save(
                new DecisionRequest(
                        "미검토", "철회", 1, "withdraw", initial.basisHash(), false, List.of()));
        assertThat(decisionService.get(ownerId, workId, reviewId).status()).isEqualTo("미검토");
        assertThat(
                        decisionService
                                .history(ownerId, workId, reviewId, OffsetPageRequest.of(0, 10))
                                .items())
                .extracting(DecisionResponse::version)
                .containsExactly(2L, 1L);
        assertThat(preview("c3").input().decisionReferences()).isEmpty();
        mockMvc.perform(get(path())).andExpect(status().isUnauthorized());
        mockMvc.perform(
                        put(path())
                                .with(authentication(owner))
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        UsernamePasswordAuthenticationToken other =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(-1L, "other@example.com", "타인"), null, List.of());
        mockMvc.perform(get(path()).with(authentication(other))).andExpect(status().isNotFound());
        mockMvc.perform(get(path() + "/history").with(authentication(owner)).param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/work/" + workId + "/decision").with(authentication(owner)))
                .andExpect(jsonPath("$.data.items[0].status").value("미검토"));
    }

    @Test
    void onlyRelatedChaptersAndScenesReceiveDecisionAndCandidatesStayUnreviewed() {
        DecisionResponse decision = save(intent(0, List.of(range("c2", "c3"))));
        assertThat(preview("c1").input().decisionReferences()).isEmpty();
        assertThat(preview("c2").input().decisionReferences()).hasSize(1);
        AnalysisPreviewResponse next = preview("c3");
        assertThat(next.input().decisionReferences().getFirst().version()).isEqualTo(1);
        assertThat(next.input().decisionReferences().getFirst().conditionText()).contains("주인공 능력");
        assertThat(
                        resultProcessor
                                .validate(provider(), next.input())
                                .reviews()
                                .getFirst()
                                .decision())
                .isEqualTo("미검토");
        assertThat(preview("c4").input().decisionReferences()).isEmpty();
        Long unrelated =
                settingService
                        .create(
                                ownerId,
                                workId,
                                new CreateSettingRequest(
                                        "다른 인물",
                                        SettingKind.CHARACTER,
                                        SettingStatus.CONFIRMED,
                                        "전혀 다른 근거"))
                        .id();
        assertThat(
                        previewService
                                .preview(
                                        ownerId,
                                        workId,
                                        request("c3", List.of(unrelated.toString())))
                                .input()
                                .decisionReferences())
                .isEmpty();
        assertThat(
                        previewService
                                .preview(
                                        ownerId,
                                        workId,
                                        request("c2", List.of(unrelated.toString())))
                                .input()
                                .decisionReferences())
                .isEmpty();
        Long source =
                jdbcTemplate.queryForObject(
                        "SELECT target_source_id FROM review_candidates WHERE id=?",
                        Long.class,
                        reviewId);
        save(
                intent(
                        1,
                        List.of(
                                new DecisionRequest.Scope(
                                        "SCENE", null, null, source, 0, 5, "이 장면의 반전에만 적용"))));
        assertThat(preview("c2").input().decisionReferences()).hasSize(1);
        assertThat(preview("c3").input().decisionReferences()).isEmpty();
        assertThat(decision.scopes()).hasSize(1);
    }

    @Test
    void aDecisionDoesNotTransmitUnselectedEvidenceThroughItsContext() {
        Long other =
                settingService
                        .create(
                                ownerId,
                                workId,
                                new CreateSettingRequest(
                                        "다른 근거",
                                        SettingKind.CHARACTER,
                                        SettingStatus.CONFIRMED,
                                        "다른 근거"))
                        .id();
        AnalysisRequest selection = request("c2", List.of(settingId.toString(), other.toString()));
        AnalysisPreviewResponse preview = previewService.preview(ownerId, workId, selection);
        String combinedJob =
                jobService
                        .create(
                                ownerId,
                                workId,
                                selection.confirmed(
                                        preview.previewToken(), preview.policyVersion()))
                        .id();
        stateService.claim(combinedJob).orElseThrow();
        AnalysisOutput response =
                new AnalysisOutput(
                        List.of(
                                new AnalysisOutput.Candidate(
                                        "설정 충돌 후보",
                                        "마법 사용",
                                        0,
                                        5,
                                        List.of(
                                                new AnalysisOutput.Evidence(
                                                        "SETTING",
                                                        settingId.toString(),
                                                        "마법 불가",
                                                        0,
                                                        5,
                                                        "능력"),
                                                new AnalysisOutput.Evidence(
                                                        "SETTING",
                                                        other.toString(),
                                                        "다른 근거",
                                                        0,
                                                        5,
                                                        "복합 근거")),
                                        "두 근거를 함께 비교",
                                        "계기 확인")));
        stateService.succeed(
                combinedJob,
                resultProcessor.validateDetailed(
                        mapper.writeValueAsString(response), preview.input()));
        reviewId = jobService.get(ownerId, workId, combinedJob).result().reviews().getFirst().id();
        save(intent(0, List.of(range("c2", null))));
        assertThat(preview("c3").input().decisionReferences()).isEmpty();
        assertThat(
                        previewService
                                .preview(
                                        ownerId,
                                        workId,
                                        request(
                                                "c3",
                                                List.of(settingId.toString(), other.toString())))
                                .input()
                                .decisionReferences())
                .hasSize(1);
    }

    @Test
    void immutableReferencesRejectChangedPreviewAndChangedBasisNeedsReconfirmation() {
        save(intent(0, List.of(range("c2", null))));
        AnalysisRequest request = request("c3", List.of(settingId.toString()));
        AnalysisPreviewResponse preview = previewService.preview(ownerId, workId, request);
        String nextJob =
                jobService
                        .create(
                                ownerId,
                                workId,
                                request.confirmed(preview.previewToken(), preview.policyVersion()))
                        .id();
        jobService.cancel(ownerId, workId, nextJob);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM analysis_decision_references WHERE job_id=?",
                                Long.class,
                                nextJob))
                .isEqualTo(1);
        settingService.update(
                ownerId,
                workId,
                settingId,
                new UpdateSettingRequest(
                        "능력", SettingKind.CHARACTER, SettingStatus.CONFIRMED, "마법 불가. 봉인 기록", 0L));
        assertThat(preview("c3").input().decisionReferences()).isEmpty();
        DecisionResponse stale = decisionService.get(ownerId, workId, reviewId);
        assertThat(stale.stale()).isTrue();
        assertThat(stale.canReconfirm()).isTrue();
        DecisionRequest update = intent(1, List.of(range("c2", null)));
        assertThatThrownBy(() -> save(update)).isInstanceOf(CustomException.class);
        save(
                new DecisionRequest(
                        update.status(),
                        "바뀐 근거를 확인함",
                        1,
                        update.mutationId(),
                        update.basisHash(),
                        true,
                        update.scopes()));
        assertThat(preview("c3").input().decisionReferences().getFirst().version()).isEqualTo(2);
        assertThat(
                        snapshotService
                                .get(ownerId, workId, nextJob)
                                .input()
                                .decisionReferences()
                                .getFirst()
                                .version())
                .isEqualTo(1);
        assertThat(snapshotService.get(ownerId, workId, nextJob).stale()).isTrue();
        AnalysisRequest prepared = request("c4", List.of(settingId.toString()));
        AnalysisPreviewResponse beforeWithdrawal =
                previewService.preview(ownerId, workId, prepared);
        DecisionResponse current = decisionService.get(ownerId, workId, reviewId);
        save(new DecisionRequest("미검토", "철회", 2, "retract", current.basisHash(), false, List.of()));
        assertThatThrownBy(
                        () ->
                                jobService.create(
                                        ownerId,
                                        workId,
                                        prepared.confirmed(
                                                beforeWithdrawal.previewToken(),
                                                beforeWithdrawal.policyVersion())))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void changedQuotedTextRequiresReanalysisButCanBeWithdrawn() {
        save(intent(0, List.of(range("c2", null))));
        settingService.update(
                ownerId,
                workId,
                settingId,
                new UpdateSettingRequest(
                        "능력", SettingKind.CHARACTER, SettingStatus.CONFIRMED, "마법 허용", 0L));
        DecisionResponse latest = decisionService.get(ownerId, workId, reviewId);
        assertThat(latest.canReconfirm()).isFalse();
        assertThatThrownBy(
                        () ->
                                save(
                                        new DecisionRequest(
                                                "의도된 설정",
                                                "확인",
                                                1,
                                                "changed",
                                                latest.basisHash(),
                                                true,
                                                List.of(range("c2", null)))))
                .isInstanceOf(CustomException.class);
        assertThat(
                        save(new DecisionRequest(
                                        "미검토",
                                        "철회",
                                        1,
                                        "withdraw-changed",
                                        latest.basisHash(),
                                        false,
                                        List.of()))
                                .version())
                .isEqualTo(2);
    }

    @Test
    void invalidScopeReasonMutationAndForeignChapterAreRejected() {
        DecisionRequest valid = intent(0, List.of(range("c2", "c3")));
        for (List<DecisionRequest.Scope> scopes :
                List.of(
                        List.<DecisionRequest.Scope>of(),
                        List.of(range("c4", "c2")),
                        List.of(range("foreign", null)),
                        List.of(
                                new DecisionRequest.Scope(
                                        "SCENE", null, null, 999999L, 0, 5, "조건")))) {
            assertThatThrownBy(() -> save(intent(0, scopes))).isInstanceOf(CustomException.class);
        }
        assertThatThrownBy(
                        () ->
                                save(
                                        new DecisionRequest(
                                                "의도된 설정",
                                                " ",
                                                0,
                                                "empty",
                                                valid.basisHash(),
                                                false,
                                                valid.scopes())))
                .isInstanceOf(CustomException.class);
        save(valid);
        assertThatThrownBy(
                        () ->
                                save(
                                        new DecisionRequest(
                                                "보류",
                                                "다른 내용",
                                                0,
                                                valid.mutationId(),
                                                valid.basisHash(),
                                                false,
                                                List.of())))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void concurrentSavesHaveOneWinnerAndRetentionKeepsDecisionRoots() throws Exception {
        DecisionRequest first = intent(0, List.of(range("c2", "c3"))),
                second = intent(0, List.of(range("c2", null)));
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results =
                    callers.invokeAll(
                            List.of(() -> attempt(first, barrier), () -> attempt(second, barrier)));
            assertThat(
                            List.of(
                                    results.get(0).get(10, TimeUnit.SECONDS),
                                    results.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(
                        jobRepository.findExpiredResultIds(
                                List.of(AnalysisJobStatus.SUCCEEDED),
                                Instant.now().plusSeconds(60),
                                PageRequest.of(0, 100)))
                .doesNotContain(jobId);
        assertThat(jobRepository.deleteExpiredWithoutDecisions(List.of(jobId))).isZero();
        assertThat(jobService.get(ownerId, workId, jobId).status()).isEqualTo("SUCCEEDED");
    }

    private boolean attempt(DecisionRequest request, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            save(request);
            return true;
        } catch (CustomException error) {
            assertThat(error.getErrorCode()).isEqualTo(DecisionErrorCode.CONFLICT);
            return false;
        }
    }
}
