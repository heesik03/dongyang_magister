package com.magister.backend.domain.analysis;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.analysis.converter.AnalysisConverter;
import com.magister.backend.domain.analysis.dto.AnalysisInput;
import com.magister.backend.domain.analysis.entity.AnalysisJob;
import com.magister.backend.domain.analysis.entity.AnalysisSource;
import com.magister.backend.domain.analysis.entity.ReviewCandidate;
import com.magister.backend.domain.analysis.entity.ReviewEvidence;
import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.repository.AnalysisSourceRepository;
import com.magister.backend.domain.analysis.repository.ReviewCandidateRepository;
import com.magister.backend.domain.analysis.repository.ReviewEvidenceRepository;
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
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:analysis-enhancement;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewContentEnhancementTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkRepository workRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private WorkSettingRepository settingRepository;
    @Autowired private AnalysisJobRepository jobRepository;
    @Autowired private AnalysisSourceRepository sourceRepository;
    @Autowired private ReviewCandidateRepository candidateRepository;
    @Autowired private ReviewEvidenceRepository evidenceRepository;
    @Autowired private ReviewDecisionRepository decisionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UsernamePasswordAuthenticationToken ownerAuth;
    private Work work;
    private WorkSetting setting1;
    private WorkSetting setting2;
    private Chapter chapter1;
    private Chapter chapter2;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM review_decisions");
        jdbcTemplate.update("DELETE FROM review_evidences");
        jdbcTemplate.update("DELETE FROM review_candidates");
        jdbcTemplate.update("DELETE FROM analysis_sources");
        jdbcTemplate.update("DELETE FROM analysis_jobs");
        jdbcTemplate.update("DELETE FROM chapters");
        jdbcTemplate.update("DELETE FROM work_settings");
        jdbcTemplate.update("DELETE FROM works");
        jdbcTemplate.update("DELETE FROM users");

        User author =
                userRepository.saveAndFlush(
                        new User(UUID.randomUUID() + "@example.com", "작가", "hash"));
        ownerAuth =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(author.getId(), author.getEmail(), author.getName()),
                        null,
                        List.of());
        work = workRepository.saveAndFlush(new Work(author, "검토 시각화 작품", "판타지", "중세", "설명"));
        setting1 =
                settingRepository.saveAndFlush(
                        new WorkSetting(
                                work, "마법 제약", SettingKind.WORLD, SettingStatus.CONFIRMED, "영창 필수"));
        setting2 =
                settingRepository.saveAndFlush(
                        new WorkSetting(
                                work,
                                "검술 서약",
                                SettingKind.CHARACTER,
                                SettingStatus.CONFIRMED,
                                "양손검 금지"));
        chapter1 =
                chapterRepository.saveAndFlush(
                        new Chapter(work, "ch-1", 1, "1화 발단", "무영창 마법을 시전했다.", 2L, "m1"));
        chapter2 =
                chapterRepository.saveAndFlush(
                        new Chapter(work, "ch-2", 2, "2화 전개", "수정된 2화 원고", 3L, "m2"));
        chapterRepository.saveAndFlush(
                new Chapter(work, "ch-3", 3, "3화 절정", "아직 분석하지 않은 원고", 1L, "m3"));
    }

    private String validInputJson(Chapter chapter, List<WorkSetting> settings) {
        return objectMapper.writeValueAsString(
                new AnalysisInput(
                        new ChapterData(
                                chapter.getChapterKey(),
                                chapter.getTitle(),
                                chapter.getContent(),
                                chapter.getRevision()),
                        settings.stream().map(AnalysisConverter::toReference).toList(),
                        List.of(),
                        List.of(),
                        true,
                        true));
    }

    @Test
    void overviewDistinguishesChapterStatusesAndPreventsDuplicateCounting() throws Exception {
        AnalysisJob ch1OldJob =
                new AnalysisJob(
                        "job-ch1-old",
                        work,
                        "req-old",
                        "{}",
                        validInputJson(chapter1, List.of(setting1)));
        ch1OldJob.recordConfirmation(chapter1, "v1", "gemini");
        ch1OldJob.start();
        ch1OldJob.succeed("[]");
        ch1OldJob.recordEvidenceResult();
        jobRepository.saveAndFlush(ch1OldJob);
        AnalysisSource ch1OldTarget =
                sourceRepository.saveAndFlush(
                        new AnalysisSource(
                                ch1OldJob,
                                chapter1,
                                null,
                                "TARGET",
                                "ch-1",
                                2L,
                                "1화 발단",
                                "무영창 마법을 시전했다.",
                                "{}",
                                "hash-1"));
        candidateRepository.saveAndFlush(
                new ReviewCandidate(
                        "cand-old-1",
                        ch1OldJob,
                        ch1OldTarget,
                        "설정 충돌 후보",
                        "무영창 마법",
                        0,
                        6,
                        "이전 재분석 지적",
                        "이전 제안"));

        AnalysisJob ch1LatestJob =
                new AnalysisJob(
                        "job-ch1-latest",
                        work,
                        "req-latest",
                        "{}",
                        validInputJson(chapter1, List.of(setting1)));
        ch1LatestJob.recordConfirmation(chapter1, "v1", "gemini");
        ch1LatestJob.start();
        ch1LatestJob.succeed("[]");
        ch1LatestJob.recordEvidenceResult();
        jobRepository.saveAndFlush(ch1LatestJob);
        AnalysisSource ch1LatestTarget =
                sourceRepository.saveAndFlush(
                        new AnalysisSource(
                                ch1LatestJob,
                                chapter1,
                                null,
                                "TARGET",
                                "ch-1",
                                2L,
                                "1화 발단",
                                "무영창 마법을 시전했다.",
                                "{}",
                                "hash-1"));
        AnalysisSource ch1SettingSource =
                sourceRepository.saveAndFlush(
                        new AnalysisSource(
                                ch1LatestJob,
                                null,
                                setting1,
                                "SETTING",
                                setting1.getId().toString(),
                                1L,
                                "마법 제약",
                                "영창 필수",
                                "{}",
                                "hash-s1"));
        ReviewCandidate c1 =
                candidateRepository.saveAndFlush(
                        new ReviewCandidate(
                                "cand-latest-1",
                                ch1LatestJob,
                                ch1LatestTarget,
                                "설정 충돌 후보",
                                "무영창 마법",
                                0,
                                6,
                                "영창 설정과 다름",
                                "영창 과정을 보완"));
        evidenceRepository.saveAndFlush(
                new ReviewEvidence(c1, ch1SettingSource, "영창 필수", 0, 5, "설정 근거"));

        AnalysisJob ch2OutdatedJob =
                new AnalysisJob(
                        "job-ch2-outdated",
                        work,
                        "req-ch2",
                        "{}",
                        validInputJson(chapter2, List.of(setting1)));
        ch2OutdatedJob.recordConfirmation(chapter2, "v1", "gemini");
        ch2OutdatedJob.start();
        ch2OutdatedJob.succeed("[]");
        ch2OutdatedJob.recordEvidenceResult();
        jobRepository.saveAndFlush(ch2OutdatedJob);
        AnalysisSource ch2OutdatedTarget =
                sourceRepository.saveAndFlush(
                        new AnalysisSource(
                                ch2OutdatedJob,
                                chapter2,
                                null,
                                "TARGET",
                                "ch-2",
                                2L,
                                "2화 전개",
                                "수정 전 2화 원고",
                                "{}",
                                "hash-2-old"));
        ReviewCandidate c2 =
                candidateRepository.saveAndFlush(
                        new ReviewCandidate(
                                "cand-ch2-1",
                                ch2OutdatedJob,
                                ch2OutdatedTarget,
                                "설명 보완",
                                "수정 전",
                                0,
                                4,
                                "동기 설명 보완 필요",
                                "계기 추가"));
        decisionRepository.saveAndFlush(
                new ReviewDecision(c2, 1L, "수용", "반영 완료", "mut-d1", "{}", "basis-1"));

        mockMvc.perform(
                        get("/api/work/" + work.getId() + "/analysis/overview")
                                .with(authentication(ownerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalChapters").value(3))
                .andExpect(jsonPath("$.data.analyzedChapters").value(2))
                .andExpect(jsonPath("$.data.currentRevisionChapters").value(1))
                .andExpect(jsonPath("$.data.outdatedRevisionChapters").value(1))
                .andExpect(jsonPath("$.data.unanalyzedChapters").value(1))
                .andExpect(jsonPath("$.data.totalConflictCount").value(1))
                .andExpect(jsonPath("$.data.totalSupplementCount").value(1))
                .andExpect(jsonPath("$.data.totalUnreviewedCount").value(1))
                .andExpect(jsonPath("$.data.chapters[0].chapterId").value("ch-1"))
                .andExpect(jsonPath("$.data.chapters[0].analysisStatus").value("CURRENT_REVISION"))
                .andExpect(jsonPath("$.data.chapters[0].analysisRunCount").value(2))
                .andExpect(jsonPath("$.data.chapters[0].conflictCount").value(1))
                .andExpect(jsonPath("$.data.chapters[1].chapterId").value("ch-2"))
                .andExpect(jsonPath("$.data.chapters[1].analysisStatus").value("OUTDATED_REVISION"))
                .andExpect(jsonPath("$.data.chapters[1].acceptedCount").value(1))
                .andExpect(jsonPath("$.data.chapters[2].chapterId").value("ch-3"))
                .andExpect(jsonPath("$.data.chapters[2].analysisStatus").value("UNANALYZED"));
    }

    @Test
    void reviewReportReturnsScopeSummaryStructuredFourStepsAndRevisionComparison()
            throws Exception {
        AnalysisJob baseJob =
                new AnalysisJob(
                        "job-base",
                        work,
                        "req-base",
                        "{}",
                        validInputJson(chapter1, List.of(setting1)));
        baseJob.recordConfirmation(chapter1, "v1", "gemini");
        baseJob.start();
        baseJob.succeed("[]");
        baseJob.recordEvidenceResult();
        jobRepository.saveAndFlush(baseJob);
        AnalysisSource baseTarget =
                sourceRepository.saveAndFlush(
                        new AnalysisSource(
                                baseJob,
                                chapter1,
                                null,
                                "TARGET",
                                "ch-1",
                                1L,
                                "1화 발단",
                                "무영창 마법을 시전했다. 주문 없이 불꽃을 날렸다.",
                                "{}",
                                "hash-base"));
        AnalysisSource baseSetting1 =
                sourceRepository.saveAndFlush(
                        new AnalysisSource(
                                baseJob,
                                null,
                                setting1,
                                "SETTING",
                                setting1.getId().toString(),
                                1L,
                                "마법 제약",
                                "영창 필수",
                                "{}",
                                "hash-s1"));
        ReviewCandidate baseRetained =
                candidateRepository.saveAndFlush(
                        new ReviewCandidate(
                                "base-cand-1",
                                baseJob,
                                baseTarget,
                                "설정 충돌 후보",
                                "무영창 마법",
                                0,
                                6,
                                "영창 필수 설정과 다름",
                                "영창 묘사 추가"));
        evidenceRepository.saveAndFlush(
                new ReviewEvidence(baseRetained, baseSetting1, "영창 필수", 0, 5, "마법 제약 근거"));

        ReviewCandidate baseDisappeared =
                candidateRepository.saveAndFlush(
                        new ReviewCandidate(
                                "base-cand-2",
                                baseJob,
                                baseTarget,
                                "설명 보완",
                                "주문 없이 불꽃",
                                14,
                                22,
                                "불꽃 발현 계기 미상",
                                "매개체 설명 보완"));
        evidenceRepository.saveAndFlush(
                new ReviewEvidence(baseDisappeared, baseSetting1, "영창 필수", 0, 5, "마법 제약 근거"));

        AnalysisJob currentJob =
                new AnalysisJob(
                        "job-current",
                        work,
                        "req-current",
                        "{}",
                        validInputJson(chapter1, List.of(setting1, setting2)));
        currentJob.recordConfirmation(chapter1, "v1", "gemini");
        currentJob.start();
        currentJob.succeed("[]");
        currentJob.recordEvidenceResult();
        jobRepository.saveAndFlush(currentJob);
        AnalysisSource currentTarget =
                sourceRepository.saveAndFlush(
                        new AnalysisSource(
                                currentJob,
                                chapter1,
                                null,
                                "TARGET",
                                "ch-1",
                                2L,
                                "1화 발단",
                                "무영창 마법을 시전했다. 양손검을 뽑아 들었다.",
                                "{}",
                                "hash-current"));
        AnalysisSource currentSetting1 =
                sourceRepository.saveAndFlush(
                        new AnalysisSource(
                                currentJob,
                                null,
                                setting1,
                                "SETTING",
                                setting1.getId().toString(),
                                1L,
                                "마법 제약",
                                "영창 필수",
                                "{}",
                                "hash-s1"));
        AnalysisSource currentSetting2 =
                sourceRepository.saveAndFlush(
                        new AnalysisSource(
                                currentJob,
                                null,
                                setting2,
                                "SETTING",
                                setting2.getId().toString(),
                                1L,
                                "검술 서약",
                                "양손검 금지",
                                "{}",
                                "hash-s2"));

        ReviewCandidate currentRetained =
                candidateRepository.saveAndFlush(
                        new ReviewCandidate(
                                "curr-cand-1",
                                currentJob,
                                currentTarget,
                                "설정 충돌 후보",
                                "무영창 마법",
                                0,
                                6,
                                "영창 필수 설정과 다름",
                                "영창 묘사 추가"));
        evidenceRepository.saveAndFlush(
                new ReviewEvidence(
                        currentRetained, currentSetting1, "영창 필수", 0, 5, "마법 제약 근거"));

        ReviewCandidate currentScopeAdded =
                candidateRepository.saveAndFlush(
                        new ReviewCandidate(
                                "curr-cand-2",
                                currentJob,
                                currentTarget,
                                "설정 충돌 후보",
                                "양손검",
                                14,
                                17,
                                "양손검 금지 서약과 충돌 가능성",
                                "서약 해제 조건 보완"));
        evidenceRepository.saveAndFlush(
                new ReviewEvidence(
                        currentScopeAdded, currentSetting2, "양손검 금지", 0, 6, "검술 서약 근거"));

        mockMvc.perform(
                        get(
                                        "/api/work/"
                                                + work.getId()
                                                + "/analysis/"
                                                + currentJob.getId()
                                                + "/review")
                                .with(authentication(ownerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalCandidates").value(2))
                .andExpect(jsonPath("$.data.summary.settingCount").value(2))
                .andExpect(jsonPath("$.data.summary.priorityItems[0].candidateIds[0]").exists())
                .andExpect(jsonPath("$.data.candidates.items[0].confirmedFact").isNotEmpty())
                .andExpect(jsonPath("$.data.candidates.items[0].interpretation").isNotEmpty())
                .andExpect(jsonPath("$.data.candidates.items[0].missingInfo").isNotEmpty())
                .andExpect(jsonPath("$.data.candidates.items[0].options[0].premise").isNotEmpty());

        mockMvc.perform(
                        get(
                                        "/api/work/"
                                                + work.getId()
                                                + "/analysis/"
                                                + currentJob.getId()
                                                + "/compare")
                                .param("baseJobId", baseJob.getId())
                                .with(authentication(ownerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasBaseJob").value(true))
                .andExpect(jsonPath("$.data.scopeChanged").value(true))
                .andExpect(jsonPath("$.data.manuscriptModified").value(true))
                .andExpect(jsonPath("$.data.retained[0].candidateId").value("curr-cand-1"))
                .andExpect(
                        jsonPath("$.data.retained[0].matchedPreviousCandidateId")
                                .value("base-cand-1"))
                .andExpect(jsonPath("$.data.notReappeared[0].candidateId").value("base-cand-2"))
                .andExpect(jsonPath("$.data.incomparable[0].candidateId").value("curr-cand-2"));
    }
}
