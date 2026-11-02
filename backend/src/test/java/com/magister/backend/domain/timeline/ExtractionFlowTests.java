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
import com.magister.backend.domain.timeline.dto.ExtractionData.Preview;
import com.magister.backend.domain.timeline.dto.ExtractionData.PreviewRequest;
import com.magister.backend.domain.timeline.dto.ExtractionData.SaveCandidates;
import com.magister.backend.domain.timeline.dto.ExtractionData.Selection;
import com.magister.backend.domain.timeline.service.ExtractionDispatcher;
import com.magister.backend.domain.timeline.service.ExtractionService;
import com.magister.backend.domain.timeline.service.ExtractionWorker;
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
    @Autowired ExtractionService service;
    @Autowired ExtractionWorker worker;
    @Autowired WorkspaceService workspace;
    @Autowired ChapterService chapters;
    @Autowired UserRepository users;
    @Autowired WorkRepository works;
    @Autowired MockMvc mockMvc;
    @MockitoBean ExtractionDispatcher dispatcher;
    @MockitoBean GeminiClient gemini;
    private Long ownerId;
    private Long workId;

    @BeforeEach
    void prepare() {
        User author =
                users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "작가", "hash"));
        ownerId = author.getId();
        workId = works.saveAndFlush(new Work(author, "타임라인 검증", "판타지", "", "")).getId();
        chapters.create(
                ownerId, workId, new CreateChapterRequest("chapter", "1화", MANUSCRIPT, "create"));
    }

    private CreateRequest request() {
        List<Selection> selection = List.of(new Selection("chapter", 1));
        Preview preview = service.preview(ownerId, workId, new PreviewRequest(selection));
        return new CreateRequest(selection, preview.token(), true, UUID.randomUUID().toString());
    }

    private Job succeeded() {
        when(gemini.generateEvents(anyString()))
                .thenReturn(
                        """
                        {"events":[{"title":"왕도로 출발","description":"린이 떠남","time":"사흘 뒤",
                        "characters":"린","chapterId":"chapter","quote":"린은 떠났다.","occurrence":1}]}
                        """);
        Job created = service.create(ownerId, workId, request());
        worker.execute(created.id());
        return service.get(ownerId, workId, created.id());
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
        assertThat(workspace.get(ownerId, workId).content().events()).isEmpty();
        assertThat(service.source(ownerId, workId, completed.id()).sources().getFirst().content())
                .isEqualTo(MANUSCRIPT);
    }

    @Test
    void confirmationPreservesManualEventsAndIsIdempotentAfterReload() {
        EventData manual = new EventData("manual", "기존 수동 사건", "1일차", "린", "chapter", "", "");
        WorkspaceResponse before =
                workspace.save(
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
                service.save(
                        ownerId,
                        workId,
                        completed.id(),
                        new SaveCandidates(
                                completed.version(),
                                List.of(include(completed.candidates().getFirst()))));
        WorkspaceResponse confirmed =
                service.confirm(
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
                        service.confirm(
                                        ownerId,
                                        workId,
                                        edited.id(),
                                        edited.version(),
                                        before.version(),
                                        "confirm")
                                .content()
                                .events())
                .hasSize(2);
        assertThat(service.get(ownerId, workId, edited.id()).candidates().getFirst().title())
                .isEqualTo("작가가 확인한 사건");
    }

    @Test
    void changedPreviewAndUnconfirmedConsentNeverCallProvider() {
        CreateRequest prepared = request();
        chapters.update(
                ownerId, workId, "chapter", new UpdateChapterRequest("1화", "바뀐 원고", 0, "change"));
        assertThatThrownBy(() -> service.create(ownerId, workId, prepared))
                .isInstanceOf(CustomException.class);
        CreateRequest noConsent =
                new CreateRequest(
                        List.of(new Selection("chapter", 2)),
                        prepared.token(),
                        false,
                        "no-consent");
        assertThatThrownBy(() -> service.create(ownerId, workId, noConsent))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(gemini);
    }

    @Test
    void requestReplayAndCancellationDoNotRunTwice() {
        CreateRequest request = request();
        Job created = service.create(ownerId, workId, request);
        assertThat(service.create(ownerId, workId, request).id()).isEqualTo(created.id());
        service.cancel(ownerId, workId, created.id());
        worker.execute(created.id());
        assertThat(service.get(ownerId, workId, created.id()).status()).isEqualTo("CANCELLED");
        verifyNoInteractions(gemini);
    }

    @Test
    void invalidProviderEvidenceFailsExplicitlyWithoutChangingTimeline() {
        when(gemini.generateEvents(anyString()))
                .thenReturn(
                        """
                        {"events":[{"title":"없는 사건","description":"","time":"","characters":"","chapterId":"chapter","quote":"없는 문장","occurrence":0}]}
                        """);
        Job created = service.create(ownerId, workId, request());
        worker.execute(created.id());
        assertThat(service.get(ownerId, workId, created.id()).status()).isEqualTo("FAILED");
        assertThat(workspace.get(ownerId, workId).content().events()).isEmpty();
    }

    @Test
    void staleDraftVersionAndForeignOwnerAreRejected() {
        Job completed = succeeded();
        service.save(
                ownerId,
                workId,
                completed.id(),
                new SaveCandidates(
                        completed.version(), List.of(include(completed.candidates().getFirst()))));
        assertThatThrownBy(
                        () ->
                                service.save(
                                        ownerId,
                                        workId,
                                        completed.id(),
                                        new SaveCandidates(
                                                completed.version(), completed.candidates())))
                .isInstanceOf(CustomException.class);
        User stranger =
                users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "다른 작가", "hash"));
        assertThatThrownBy(() -> service.source(stranger.getId(), workId, completed.id()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void savedCandidateReplayReturnsCurrentVersionWithoutOverwriting() {
        Job completed = succeeded();
        SaveCandidates request =
                new SaveCandidates(
                        completed.version(), List.of(include(completed.candidates().getFirst())));
        Job saved = service.save(ownerId, workId, completed.id(), request);
        Job replay = service.save(ownerId, workId, completed.id(), request);
        assertThat(replay.version()).isEqualTo(saved.version());
        assertThat(replay.candidates()).isEqualTo(saved.candidates());
    }

    @Test
    void sourceSnapshotSurvivesManuscriptChangesAndWorkspaceConflict() {
        Job completed = succeeded();
        Job edited =
                service.save(
                        ownerId,
                        workId,
                        completed.id(),
                        new SaveCandidates(
                                completed.version(),
                                List.of(include(completed.candidates().getFirst()))));
        chapters.update(
                ownerId, workId, "chapter", new UpdateChapterRequest("1화", "원고 수정", 0, "change"));
        Input original = service.source(ownerId, workId, edited.id());
        assertThat(original.sources().getFirst().content()).isEqualTo(MANUSCRIPT);
        workspace.save(
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
                                service.confirm(
                                        ownerId,
                                        workId,
                                        edited.id(),
                                        edited.version(),
                                        0,
                                        "confirm"))
                .isInstanceOf(CustomException.class);
        assertThat(service.get(ownerId, workId, edited.id()).candidates()).hasSize(1);
        WorkspaceResponse confirmed =
                service.confirm(ownerId, workId, edited.id(), edited.version(), 1, "confirm");
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
}
