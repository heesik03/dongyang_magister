package com.magister.backend.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.dto.AnalysisJobResponse;
import com.magister.backend.domain.analysis.dto.AnalysisRequest;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisJobStatus;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.service.AnalysisJobService;
import com.magister.backend.domain.analysis.service.execution.AnalysisWorker;
import com.magister.backend.domain.analysis.service.state.AnalysisJobStateService;
import com.magister.backend.domain.analysis.service.state.AnalysisLeaseService;
import com.magister.backend.domain.analysis.service.execution.AnalysisDispatcher;
import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;
import com.magister.backend.domain.workspace.entity.Workspace;
import com.magister.backend.domain.workspace.repository.WorkspaceRepository;
import com.magister.backend.global.error.code.CommonErrorCode;
import com.magister.backend.global.error.exception.CustomException;
import com.magister.backend.global.gemini.GeminiClient;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:analysis-lifecycle;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.magister.backend.domain.analysis.AnalysisJobLifecycleTests$SqlCapture"
})
@ActiveProfiles("test")
public class AnalysisJobLifecycleTests {
    @Autowired UserRepository users;
    @Autowired WorkRepository works;
    @Autowired WorkSettingRepository settings;
    @Autowired WorkspaceRepository workspaces;
    @Autowired AnalysisJobRepository jobs;
    @Autowired AnalysisJobService service;
    @Autowired AnalysisJobStateService states;
    @Autowired AnalysisWorker worker;
    @Autowired AnalysisDispatcher dispatcher;
    @Autowired AnalysisLeaseService leases;
    @Autowired TransactionTemplate transaction;
    @Autowired ObjectMapper json;
    @MockitoSpyBean ThreadPoolExecutor analysisExecutor;
    @MockitoBean GeminiClient gemini;

    private record Fixture(Long ownerId, Long workId, Long settingId) {
        AnalysisRequest request() {
            return new AnalysisRequest(UUID.randomUUID().toString(), "c1", 1,
                    List.of(settingId.toString()), List.of(), true, true);
        }
    }

    private Fixture fixture(Long ownerId) {
        Long authorId = ownerId == null
                ? users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "author", "hash")).getId()
                : ownerId;
        return transaction.execute(status -> {
            Work work = works.saveAndFlush(new Work(users.getReferenceById(authorId), "work", "fantasy", "", ""));
            Workspace workspace = new Workspace(work);
            workspace.replace(json.writeValueAsString(new WorkspaceData(
                    List.of(new ChapterData("c1", "chapter", "original text", 1)), List.of(), List.of())), "initial");
            workspaces.saveAndFlush(workspace);
            Long settingId = settings.saveAndFlush(new WorkSetting(work, "rule", SettingKind.WORLD,
                    SettingStatus.CONFIRMED, "evidence")).getId();
            return new Fixture(authorId, work.getId(), settingId);
        });
    }

    @Test
    void rejectedDispatchIsCommittedAsFailed() {
        Fixture fixture = fixture(null);
        doThrow(new RejectedExecutionException()).when(analysisExecutor).execute(any(Runnable.class));
        AnalysisJobResponse response = service.create(fixture.ownerId(), fixture.workId(), fixture.request());
        assertThat(jobs.findById(response.id()).orElseThrow().getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
    }

    @Test
    void malformedClaimIsRecordedAsFailed() {
        Fixture fixture = fixture(null);
        String jobId = saveJob(fixture, "invalid-json");
        worker.execute(jobId);
        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        verifyNoInteractions(gemini);
    }

    @Test
    void duplicateDeliveryDoesNotStartRunningJobAgain() {
        Fixture fixture = fixture(null);
        AnalysisInput input = new AnalysisInput(new ChapterData("c1", "chapter", "text", 1),
                List.of(), List.of(), List.of(), true, true);
        String jobId = saveJob(fixture, json.writeValueAsString(input));
        assertThat(states.claim(jobId)).isPresent();
        worker.execute(jobId);
        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
        verifyNoInteractions(gemini);
    }

    @Test
    void simultaneousRequestsAcrossWorksRespectOneJobPerOwner() throws Exception {
        Fixture first = fixture(null);
        Fixture second = fixture(first.ownerId());
        doNothing().when(analysisExecutor).execute(any(Runnable.class));
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = callers.invokeAll(List.of(
                    attempt(first, barrier), attempt(second, barrier)));
            List<Boolean> accepted = new ArrayList<>();
            for (Future<Boolean> result : results) accepted.add(result.get(10, TimeUnit.SECONDS));
            assertThat(accepted).containsExactlyInAnyOrder(true, false);
        }
    }

    private Callable<Boolean> attempt(Fixture fixture, CyclicBarrier barrier) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                service.create(fixture.ownerId(), fixture.workId(), fixture.request());
                return true;
            } catch (CustomException error) {
                assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
                return false;
            }
        };
    }

    @Test
    void pollingReadsOnlyProjectionWithoutWriteLock() {
        Fixture fixture = fixture(null);
        String jobId = saveJob(fixture, "large-input-snapshot");
        SqlCapture.QUERIES.get().clear();
        assertThat(service.get(fixture.ownerId(), fixture.workId(), jobId).status()).isEqualTo("QUEUED");
        List<String> queries = List.copyOf(SqlCapture.QUERIES.get());
        assertThat(queries).hasSize(1);
        assertThat(queries.getFirst()).doesNotContain("for update", "request_body", ".input", "password_hash");
    }

    @Test
    void queuedCancellationRemovesTaskAndNeverCallsProvider() {
        Fixture fixture = fixture(null);
        doNothing().when(analysisExecutor).execute(any(Runnable.class));
        AnalysisJobResponse response = service.create(fixture.ownerId(), fixture.workId(), fixture.request());
        assertThat(dispatcher.activeJobIds()).contains(response.id());
        assertThat(service.cancel(fixture.ownerId(), fixture.workId(), response.id()).status()).isEqualTo("CANCELLED");
        assertThat(dispatcher.activeJobIds()).doesNotContain(response.id());
        verifyNoInteractions(gemini);
    }

    @Test
    void runningCancellationInterruptsLocalProviderAndPreservesCancelledStatus() throws Exception {
        Fixture fixture = fixture(null);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(gemini.generateText(any(String.class), eq(4096))).thenAnswer(call -> {
            started.countDown();
            try {
                released.await(10, TimeUnit.SECONDS);
                return "{\"reviews\":[]}";
            } catch (InterruptedException error) {
                interrupted.countDown();
                throw new CancellationException();
            }
        });
        AnalysisJobResponse response = service.create(fixture.ownerId(), fixture.workId(), fixture.request());
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(service.cancel(fixture.ownerId(), fixture.workId(), response.id()).status()).isEqualTo("CANCELLED");
            assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(service.get(fixture.ownerId(), fixture.workId(), response.id()).status()).isEqualTo("CANCELLED");
        } finally {
            released.countDown();
        }
    }

    @Test
    void recoveryFailsOnlyExpiredLeasesAndPreservesOtherServersLiveJobs() {
        Fixture fixture = fixture(null);
        String liveId = saveLeasedJob(fixture, Instant.now().plusSeconds(120));
        String expiredId = saveLeasedJob(fixture, Instant.now().minusSeconds(1));
        leases.maintain(List.of());
        assertThat(jobs.findById(liveId).orElseThrow().getStatus()).isEqualTo(AnalysisJobStatus.QUEUED);
        assertThat(jobs.findById(expiredId).orElseThrow().getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
    }

    private String saveLeasedJob(Fixture fixture, Instant until) {
        return transaction.execute(status -> {
            AnalysisJob job = new AnalysisJob(UUID.randomUUID().toString(), works.findById(fixture.workId()).orElseThrow(),
                    UUID.randomUUID().toString(), "{}", "{}");
            job.assignLease("another-server", until);
            return jobs.saveAndFlush(job).getId();
        });
    }

    private String saveJob(Fixture fixture, String input) {
        return transaction.execute(status -> jobs.saveAndFlush(new AnalysisJob(UUID.randomUUID().toString(),
                works.findById(fixture.workId()).orElseThrow(), UUID.randomUUID().toString(), "{}", input)).getId());
    }

    public static class SqlCapture implements StatementInspector {
        static final ThreadLocal<List<String>> QUERIES = ThreadLocal.withInitial(ArrayList::new);

        @Override
        public String inspect(String sql) {
            QUERIES.get().add(sql.toLowerCase());
            return sql;
        }
    }
}
