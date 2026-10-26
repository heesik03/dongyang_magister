package com.magister.backend.domain.chapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.analysis.repository.AnalysisJobRepository;
import com.magister.backend.domain.analysis.service.AnalysisDispatcher;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.domain.workspace.repository.WorkspaceRepository;

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
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChapterApiTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired WorkRepository workRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired AnalysisJobRepository analysisJobRepository;
    @MockitoBean AnalysisDispatcher analysisDispatcher;
    private Long workId;
    private UsernamePasswordAuthenticationToken owner;

    @BeforeEach
    void prepare() {
        User user =
                userRepository.saveAndFlush(
                        new User(UUID.randomUUID() + "@example.com", "작가", "hash"));
        workId = workRepository.saveAndFlush(new Work(user, "작품", "판타지", "", "")).getId();
        owner =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(user.getId(), user.getEmail(), user.getName()),
                        null,
                        List.of());
    }

    private String path() {
        return "/api/work/" + workId + "/chapter";
    }

    private String createBody(String id, String text) {
        return objectMapper.writeValueAsString(
                Map.of(
                        "id",
                        id,
                        "title",
                        "회차 " + id,
                        "content",
                        text,
                        "mutationId",
                        "create-" + id));
    }

    private String updateBody(long version, String mutation, String text) {
        return objectMapper.writeValueAsString(
                Map.of(
                        "version",
                        version,
                        "title",
                        "수정 제목",
                        "content",
                        text,
                        "mutationId",
                        mutation));
    }

    private void create(String id, String text) throws Exception {
        mockMvc.perform(
                        post(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(createBody(id, text)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    void savesReloadsDeduplicatesAndRejectsStaleVersion() throws Exception {
        create("c1", "첫 원고");
        create("c1", "첫 원고");
        String body = updateBody(0, "edit-1", "저장 원고");
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(
                            put(path() + "/c1")
                                    .with(authentication(owner))
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.version").value(1))
                    .andExpect(jsonPath("$.data.revision").value(2));
        }
        mockMvc.perform(
                        put(path() + "/c1")
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(updateBody(0, "other-tab", "충돌 원고")))
                .andExpect(status().isConflict());
        mockMvc.perform(
                        put(path() + "/c1")
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(updateBody(1, "edit-1", "키 오용")))
                .andExpect(status().isConflict());
        mockMvc.perform(get(path() + "/c1").with(authentication(owner)))
                .andExpect(jsonPath("$.data.content").value("저장 원고"));
    }

    @Test
    void differentChaptersAndWorkspaceUseIndependentVersions() throws Exception {
        create("c1", "1화");
        create("c2", "2화");
        mockMvc.perform(
                        put(path() + "/c2")
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(updateBody(0, "edit-c2", "새 2화")))
                .andExpect(status().isOk());
        mockMvc.perform(
                        put(path() + "/c1")
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(updateBody(0, "edit-c1", "새 1화")))
                .andExpect(status().isOk());
        String workspacePath = "/api/work/" + workId + "/workspace";
        mockMvc.perform(
                        put(workspacePath)
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "version",
                                                        0,
                                                        "mutationId",
                                                        "decision-save",
                                                        "separateChapters",
                                                        true,
                                                        "content",
                                                        Map.of(
                                                                "chapters",
                                                                List.of(),
                                                                "analyses",
                                                                List.of(),
                                                                "events",
                                                                List.of())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.content.chapters[0].content").value("새 1화"));
        assertThat(workspaceRepository.findById(workId).orElseThrow().getContent())
                .contains("\"chapters\":[]");
        mockMvc.perform(
                        put(path() + "/c1")
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(updateBody(1, "edit-after-decision", "최신 1화")))
                .andExpect(status().isOk());
    }

    @Test
    void checksOwnershipCsrfAndValidation() throws Exception {
        create("c1", "비공개 원고");
        UsernamePasswordAuthenticationToken other =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(-1L, "other@example.com", "다른 작가"), null, List.of());
        mockMvc.perform(get(path()).with(authentication(other))).andExpect(status().isNotFound());
        mockMvc.perform(get(path() + "/c1").with(authentication(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(path())).andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post(path())
                                .with(authentication(owner))
                                .contentType("application/json")
                                .content(createBody("c2", "원고")))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post(path())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(createBody("c2", "가".repeat(200001))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        put(path() + "/missing")
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(updateBody(0, "missing", "원고")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsInChapterOrderAndLimitsPages() throws Exception {
        create("z", "첫 회차");
        create("a", "둘째 회차");
        mockMvc.perform(
                        get(path())
                                .with(authentication(owner))
                                .param("size", "1")
                                .param("page", "1"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value("a"))
                .andExpect(jsonPath("$.data.items[0].chapterNo").value(2));
        mockMvc.perform(get(path()).with(authentication(owner)).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oldWorkspaceCannotOverwriteNativeChapter() throws Exception {
        create("c1", "새 API의 원고");
        mockMvc.perform(
                        put("/api/work/" + workId + "/workspace")
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "version",
                                                        0,
                                                        "mutationId",
                                                        "old-client",
                                                        "content",
                                                        Map.of(
                                                                "chapters",
                                                                List.of(
                                                                        Map.of(
                                                                                "id",
                                                                                "c1",
                                                                                "title",
                                                                                "오래된 제목",
                                                                                "content",
                                                                                "오래된 원고",
                                                                                "revision",
                                                                                1)),
                                                                "analyses",
                                                                List.of(),
                                                                "events",
                                                                List.of())))))
                .andExpect(status().isConflict());
        mockMvc.perform(get(path() + "/c1").with(authentication(owner)))
                .andExpect(jsonPath("$.data.content").value("새 API의 원고"));
    }

    @Test
    void analysisUsesSavedChapterAndRejectsStaleRevisionAndFutureReference() throws Exception {
        create("c1", "최신 회차 원고");
        create("c2", "미래 회차 원고");
        MvcResult setting =
                mockMvc.perform(
                                post("/api/work/" + workId + "/setting")
                                        .with(authentication(owner))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "title",
                                                                "확정 설정",
                                                                "kind",
                                                                "WORLD",
                                                                "status",
                                                                "CONFIRMED",
                                                                "content",
                                                                "검토 근거"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        String settingId =
                objectMapper
                        .readTree(setting.getResponse().getContentAsString())
                        .get("data")
                        .get("id")
                        .asString();
        String analysisPath = "/api/work/" + workId + "/analysis";
        Map<String, Object> request =
                Map.of(
                        "requestId",
                        "saved-chapter-analysis",
                        "chapterId",
                        "c1",
                        "chapterRevision",
                        1,
                        "workspaceVersion",
                        0,
                        "settingIds",
                        List.of(settingId),
                        "previousIds",
                        List.of(),
                        "consistency",
                        true,
                        "motivation",
                        true);
        mockMvc.perform(
                        post(analysisPath)
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
        assertThat(
                        analysisJobRepository
                                .findByWork_IdAndRequestId(workId, "saved-chapter-analysis")
                                .orElseThrow()
                                .getInput())
                .contains("최신 회차 원고")
                .doesNotContain("미래 회차 원고");
        Map<String, Object> stale = new HashMap<>(request);
        stale.put("requestId", "stale-chapter-analysis");
        stale.put("chapterRevision", 2);
        // 같은 트랜잭션의 첫 분석 예약을 종료해 다음 요청의 활성 작업 제한과 분리한다.
        analysisJobRepository
                .findByWork_IdAndRequestId(workId, "saved-chapter-analysis")
                .orElseThrow()
                .cancel();
        mockMvc.perform(
                        post(analysisPath)
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(stale)))
                .andExpect(status().isConflict());
        Map<String, Object> future = new HashMap<>(request);
        future.put("requestId", "future-reference-analysis");
        future.put("previousIds", List.of("c2"));
        mockMvc.perform(
                        post(analysisPath)
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(future)))
                .andExpect(status().isBadRequest());
    }
}
