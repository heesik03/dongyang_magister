package com.magister.backend.domain.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.chapter.dto.CreateChapterRequest;
import com.magister.backend.domain.chapter.dto.UpdateChapterRequest;
import com.magister.backend.domain.chapter.service.ChapterService;
import com.magister.backend.domain.timeline.dto.ExtractionData.Candidate;
import com.magister.backend.domain.timeline.dto.ExtractionData.CreateRequest;
import com.magister.backend.domain.timeline.dto.ExtractionData.Input;
import com.magister.backend.domain.timeline.dto.ExtractionData.Job;
import com.magister.backend.domain.timeline.dto.ExtractionData.SaveCandidates;
import com.magister.backend.domain.timeline.dto.ExtractionData.Selection;
import com.magister.backend.domain.timeline.dto.TimelineData.SaveEventRequest;
import com.magister.backend.domain.timeline.service.ExtractionDispatcher;
import com.magister.backend.domain.timeline.service.ExtractionService;
import com.magister.backend.domain.timeline.service.ExtractionWorker;
import com.magister.backend.domain.timeline.service.TimelineService;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.domain.workspace.dto.SaveWorkspaceRequest;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.EventData;
import com.magister.backend.domain.workspace.dto.WorkspaceResponse;
import com.magister.backend.domain.workspace.service.WorkspaceService;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.gemini.GeminiClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

@SpringBootTest(
        properties = "spring.datasource.url=jdbc:h2:mem:timeline-flow;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExtractionFlowTests {
    private static final String MANUSCRIPT = "린은 떠났다.\n\n린은 떠났다.";
    @Autowired ExtractionService extractionService;
    @Autowired ExtractionWorker extractionWorker;
    @Autowired TimelineService timelineService;
    @Autowired WorkspaceService workspaceService;
    @Autowired ChapterService chapterService;
    @Autowired UserRepository userRepository;
    @Autowired WorkRepository workRepository;
    @Autowired MockMvc mockMvc;
    @MockitoBean ExtractionDispatcher extractionDispatcher;
    @MockitoBean GeminiClient geminiClient;
    private Long ownerId;
    private Long workId;

    @BeforeEach
    void prepare() {
        User author =
                userRepository.saveAndFlush(
                        new User(UUID.randomUUID() + "@example.com", "작가", "hash"));
        ownerId = author.getId();
        workId = workRepository.saveAndFlush(new Work(author, "타임라인 검증", "판타지", "", "")).getId();
        chapterService.create(
                ownerId, workId, new CreateChapterRequest("chapter", "1화", MANUSCRIPT, "create"));
    }

    private CreateRequest request() {
        List<Selection> selection = List.of(new Selection("chapter", 1));
        return new CreateRequest(selection, UUID.randomUUID().toString());
    }

    private Job succeeded() {
        when(geminiClient.generateEvents(anyString()))
                .thenReturn(
                        """
                        {"events":[{"title":"왕도로 출발","description":"린이 떠남","time":"사흘 뒤",
                        "characters":"린","chapterId":"chapter","quote":"린은 떠났다.","occurrence":1}]}
                        """);
        Job created = extractionService.create(ownerId, workId, request());
        extractionWorker.execute(created.id());
        return extractionService.get(ownerId, workId, created.id());
    }

    private Candidate include(Candidate candidate) {
        return new Candidate(
                candidate.id(),
                "작가가 확인한 사건",
                candidate.description(),
                candidate.time(),
                candidate.characters(),
                candidate.chapterId(),
                candidate.sourceRevision(),
                candidate.startOffset(),
                candidate.endOffset(),
                candidate.quote(),
                "INCLUDE",
                "UNRESOLVED",
                2);
    }

    @Test
    void repeatedEvidenceIsLocatedAndCandidatesNeedAuthorConfirmation() {
        Job completed = succeeded();
        assertThat(completed.status()).isEqualTo("SUCCEEDED");
        Candidate candidate = completed.candidates().getFirst();
        assertThat(candidate.startOffset()).isEqualTo(MANUSCRIPT.lastIndexOf("린은 떠났다."));
        assertThat(candidate.timeStatus()).isEqualTo("UNRESOLVED");
        assertThat(workspaceService.get(ownerId, workId).content().events()).isEmpty();
        assertThat(
                        extractionService
                                .source(ownerId, workId, completed.id())
                                .sources()
                                .getFirst()
                                .content())
                .isEqualTo(MANUSCRIPT);
    }

    @Test
    void confirmationPreservesManualEventsAndIsIdempotentAfterReload() {
        EventData manual = new EventData("manual", "기존 수동 사건", "1일차", "린", "chapter", "", "");
        WorkspaceResponse before =
                workspaceService.save(
                        ownerId,
                        workId,
                        new SaveWorkspaceRequest(
                                0,
                                "manual",
                                new WorkspaceData(List.of(), List.of(), List.of(manual)),
                                true,
                                true));
        Job completed = succeeded();
        Job edited =
                extractionService.save(
                        ownerId,
                        workId,
                        completed.id(),
                        new SaveCandidates(
                                completed.version(),
                                List.of(include(completed.candidates().getFirst()))));
        WorkspaceResponse confirmed =
                extractionService.confirm(
                        ownerId,
                        workId,
                        edited.id(),
                        edited.version(),
                        before.version(),
                        "confirm");
        assertThat(confirmed.content().events()).hasSize(2);
        assertThat(confirmed.content().events().getFirst()).isEqualTo(manual);
        assertThat(confirmed.content().events().getLast().extractionId()).isEqualTo(edited.id());
        assertThat(
                        extractionService
                                .confirm(
                                        ownerId,
                                        workId,
                                        edited.id(),
                                        edited.version(),
                                        before.version(),
                                        "confirm")
                                .content()
                                .events())
                .hasSize(2);
        assertThat(extractionService.get(ownerId, workId, edited.id()).candidates().getFirst().title())
                .isEqualTo("작가가 확인한 사건");
    }

    @Test
    void changedChapterRevisionNeverCallsProvider() {
        CreateRequest prepared = request();
        chapterService.update(
                ownerId, workId, "chapter", new UpdateChapterRequest("1화", "바뀐 원고", 0, "change"));
        assertThatThrownBy(() -> extractionService.create(ownerId, workId, prepared))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(geminiClient);
    }

    @Test
    void requestReplayAndCancellationDoNotRunTwice() {
        CreateRequest request = request();
        Job created = extractionService.create(ownerId, workId, request);
        assertThat(extractionService.create(ownerId, workId, request).id()).isEqualTo(created.id());
        extractionService.cancel(ownerId, workId, created.id());
        extractionWorker.execute(created.id());
        assertThat(extractionService.get(ownerId, workId, created.id()).status())
                .isEqualTo("CANCELLED");
        verifyNoInteractions(geminiClient);
    }

    @Test
    void invalidProviderEvidenceFailsExplicitlyWithoutChangingTimeline() {
        when(geminiClient.generateEvents(anyString()))
                .thenReturn(
                        """
                        {"events":[{"title":"없는 사건","description":"","time":"","characters":"","chapterId":"chapter","quote":"없는 문장","occurrence":0}]}
                        """);
        Job created = extractionService.create(ownerId, workId, request());
        extractionWorker.execute(created.id());
        assertThat(extractionService.get(ownerId, workId, created.id()).status())
                .isEqualTo("FAILED");
        assertThat(workspaceService.get(ownerId, workId).content().events()).isEmpty();
    }

    @Test
    void staleDraftVersionAndForeignOwnerAreRejected() {
        Job completed = succeeded();
        extractionService.save(
                ownerId,
                workId,
                completed.id(),
                new SaveCandidates(
                        completed.version(), List.of(include(completed.candidates().getFirst()))));
        assertThatThrownBy(
                        () ->
                                extractionService.save(
                                        ownerId,
                                        workId,
                                        completed.id(),
                                        new SaveCandidates(
                                                completed.version(), completed.candidates())))
                .isInstanceOf(CustomException.class);
        User stranger =
                userRepository.saveAndFlush(
                        new User(UUID.randomUUID() + "@example.com", "다른 작가", "hash"));
        assertThatThrownBy(() -> extractionService.source(stranger.getId(), workId, completed.id()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void savedCandidateReplayReturnsCurrentVersionWithoutOverwriting() {
        Job completed = succeeded();
        SaveCandidates request =
                new SaveCandidates(
                        completed.version(), List.of(include(completed.candidates().getFirst())));
        Job saved = extractionService.save(ownerId, workId, completed.id(), request);
        Job replay = extractionService.save(ownerId, workId, completed.id(), request);
        assertThat(replay.version()).isEqualTo(saved.version());
        assertThat(replay.candidates()).isEqualTo(saved.candidates());
    }

    @Test
    void sourceSnapshotSurvivesManuscriptChangesAndWorkspaceConflict() {
        Job completed = succeeded();
        Job edited =
                extractionService.save(
                        ownerId,
                        workId,
                        completed.id(),
                        new SaveCandidates(
                                completed.version(),
                                List.of(include(completed.candidates().getFirst()))));
        chapterService.update(
                ownerId, workId, "chapter", new UpdateChapterRequest("1화", "원고 수정", 0, "change"));
        Input original = extractionService.source(ownerId, workId, edited.id());
        assertThat(original.sources().getFirst().content()).isEqualTo(MANUSCRIPT);
        workspaceService.save(
                ownerId,
                workId,
                new SaveWorkspaceRequest(
                        0,
                        "new-workspace",
                        new WorkspaceData(List.of(), List.of(), List.of()),
                        true,
                        true));
        assertThatThrownBy(
                        () ->
                                extractionService.confirm(
                                        ownerId,
                                        workId,
                                        edited.id(),
                                        edited.version(),
                                        0,
                                        "confirm"))
                .isInstanceOf(CustomException.class);
        assertThat(extractionService.get(ownerId, workId, edited.id()).candidates()).hasSize(1);
        WorkspaceResponse confirmed =
                extractionService.confirm(
                        ownerId, workId, edited.id(), edited.version(), 1, "confirm");
        assertThat(confirmed.content().events().getFirst().sourceRevision()).isEqualTo(1L);
    }

    @Test
    void endpointRequiresSessionAndCsrf() throws Exception {
        mockMvc.perform(
                        post("/api/work/" + workId + "/event-extraction")
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void timelineCrudPersistsEventsInDedicatedTable() {
        SaveEventRequest createReq =
                new SaveEventRequest(
                        "ev-1",
                        "첫 사건",
                        "설명",
                        "1일차",
                        "린",
                        "chapter",
                        "린은 떠났다.",
                        0,
                        "CONFIRMED",
                        1L,
                        0,
                        7,
                        null);
        EventData created = timelineService.create(ownerId, workId, createReq);
        assertThat(created.title()).isEqualTo("첫 사건");
        assertThat(timelineService.list(ownerId, workId)).hasSize(1);
        assertThat(workspaceService.get(ownerId, workId).content().events()).hasSize(1);

        SaveEventRequest updateReq =
                new SaveEventRequest(
                        "ev-1",
                        "수정된 사건",
                        "수정 설명",
                        "2일차",
                        "린",
                        "chapter",
                        "린은 떠났다.",
                        0,
                        "CONFIRMED",
                        1L,
                        0,
                        7,
                        null);
        EventData updated = timelineService.update(ownerId, workId, "ev-1", updateReq);
        assertThat(updated.title()).isEqualTo("수정된 사건");

        timelineService.delete(ownerId, workId, "ev-1");
        assertThat(timelineService.list(ownerId, workId)).isEmpty();
        assertThat(workspaceService.get(ownerId, workId).content().events()).isEmpty();
    }

    @Test
    void timelineVersionRejectsStaleReplacementAndSeparateWorkspaceSaveKeepsEvents() {
        EventData event = new EventData(
                "new-event", "새 사건", "", "", "chapter", "", "");
        long current = workspaceService.get(ownerId, workId).version();
        long saved = timelineService.replaceAll(ownerId, workId, current, List.of(event)).version();
        assertThatThrownBy(() -> timelineService.replaceAll(ownerId, workId, current, List.of()))
                .isInstanceOf(CustomException.class);
        WorkspaceResponse workspace = workspaceService.save(
                ownerId, workId, new SaveWorkspaceRequest(
                        saved, "separate-events", WorkspaceData.empty(), true, true, true));
        assertThat(workspace.content().events()).containsExactly(event);
    }

    @Test
    void timelineRejectsForgedQuoteOnDedicatedWrite() {
        SaveEventRequest invalid = new SaveEventRequest(
                "forged", "사건", "", "", "", "chapter", "없는 인용문",
                0, "UNRESOLVED", 1L, 0, 6, null);
        assertThatThrownBy(() -> timelineService.create(ownerId, workId, invalid))
                .isInstanceOf(CustomException.class);
        assertThat(timelineService.list(ownerId, workId)).isEmpty();
    }
}
