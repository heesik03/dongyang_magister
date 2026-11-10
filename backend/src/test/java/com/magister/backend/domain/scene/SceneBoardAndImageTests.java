package com.magister.backend.domain.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.chapter.entity.Chapter;
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
import com.magister.backend.domain.work.service.WorkDeletionService;
import com.magister.backend.global.gemini.GeminiClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@ActiveProfiles("test")
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:scene-board-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "scene-board.analysis-daily-limit=2",
            "scene-board.image-daily-limit=1",
            "scene-board.image-model=imagen-3.0-generate-002",
            "scene-board.poll-delay-ms=3600000",
            "scene-board.image-poll-delay-ms=3600000"
        })
@AutoConfigureMockMvc
class SceneBoardAndImageTests {
    private static final byte[] VALID_PNG = {
        (byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13
    };

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkRepository workRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private WorkSettingRepository workSettingRepository;
    @Autowired private WorkDeletionService workDeletionService;
    @Autowired private SceneBoardWorker sceneBoardWorker;
    @Autowired private SceneImageWorker sceneImageWorker;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private GeminiClient geminiClient;

    private UsernamePasswordAuthenticationToken ownerAuth;
    private UsernamePasswordAuthenticationToken otherAuth;
    private Work work;
    private WorkSetting confirmedSetting;
    private WorkSetting ideaSetting;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM scene_image_jobs");
        jdbcTemplate.update("DELETE FROM scene_boards");
        jdbcTemplate.update("DELETE FROM ai_usage_buckets");
        jdbcTemplate.update("DELETE FROM chapters");
        jdbcTemplate.update("DELETE FROM work_settings");
        jdbcTemplate.update("DELETE FROM works");
        jdbcTemplate.update("DELETE FROM users");

        User owner =
                userRepository.saveAndFlush(
                        new User("scene-owner@example.com", "작가", "unused-hash"));
        User other =
                userRepository.saveAndFlush(
                        new User("scene-other@example.com", "다른작가", "unused-hash"));
        ownerAuth =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(owner.getId(), owner.getEmail(), owner.getName()),
                        null,
                        List.of());
        otherAuth =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(other.getId(), other.getEmail(), other.getName()),
                        null,
                        List.of());

        work = workRepository.saveAndFlush(new Work(owner, "장면 작품", "추리", "근대", "소개"));
        chapterRepository.saveAndFlush(
                new Chapter(
                        work,
                        "ch-1",
                        1,
                        "1화 등대",
                        "앞문장. 등불이 깨졌다. 시계가 멈췄다. 등불이 깨졌다.",
                        2L,
                        "init"));
        confirmedSetting =
                workSettingRepository.saveAndFlush(
                        new WorkSetting(
                                work,
                                "등대 구조",
                                SettingKind.WORLD,
                                SettingStatus.CONFIRMED,
                                "등대 꼭대기에는 오래된 회중시계가 걸려 있다."));
        ideaSetting =
                workSettingRepository.saveAndFlush(
                        new WorkSetting(
                                work,
                                "미확정 메모",
                                SettingKind.WORLD,
                                SettingStatus.IDEA,
                                "초안 메모"));
    }

    @Test
    void sceneBoardValidatesSelectionIdempotencyWorkerOutputAndQuota() throws Exception {
        String basePath = "/api/work/" + work.getId() + "/scene-board";

        // 미확정 설정 포함 시 400 거절
        mvc.perform(
                        post(basePath + "/preview")
                                .with(authentication(ownerAuth))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "chapterId",
                                                        "ch-1",
                                                        "revision",
                                                        2,
                                                        "start",
                                                        5,
                                                        "end",
                                                        31,
                                                        "settingIds",
                                                        List.of(ideaSetting.getId())))))
                .andExpect(status().isBadRequest());

        // 버전 불일치 시 409 거절
        mvc.perform(
                        post(basePath + "/preview")
                                .with(authentication(ownerAuth))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "chapterId",
                                                        "ch-1",
                                                        "revision",
                                                        1,
                                                        "start",
                                                        5,
                                                        "end",
                                                        31,
                                                        "settingIds",
                                                        List.of(confirmedSetting.getId())))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 정상 미리보기 발급 ("등불이 깨졌다. 시계가 멈췄다. 등불이 깨졌다." 구간: start=5, end=31)
        Map<String, Object> selection =
                Map.of(
                        "chapterId",
                        "ch-1",
                        "revision",
                        2,
                        "start",
                        5,
                        "end",
                        31,
                        "settingIds",
                        List.of(confirmedSetting.getId()));
        String previewResponse =
                mvc.perform(
                                post(basePath + "/preview")
                                        .with(authentication(ownerAuth))
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(selection)))
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.data.snapshot.text")
                                        .value("등불이 깨졌다. 시계가 멈췄다. 등불이 깨졌다."))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String token = objectMapper.readTree(previewResponse).path("data").path("token").asString();

        // 동의(consent=false) 누락 시 거절 및 사용량 미차감
        mvc.perform(
                        post(basePath)
                                .with(authentication(ownerAuth))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "requestId",
                                                        "req-scene-1",
                                                        "selection",
                                                        selection,
                                                        "token",
                                                        token,
                                                        "consent",
                                                        false))))
                .andExpect(status().isBadRequest());

        // 정상 접수(202) 및 동일 requestId 재전송 시 추가 차감 없이 동일 작업 반환
        String createResponse =
                mvc.perform(
                                post(basePath)
                                        .with(authentication(ownerAuth))
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "requestId",
                                                                "req-scene-1",
                                                                "selection",
                                                                selection,
                                                                "token",
                                                                token,
                                                                "consent",
                                                                true))))
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.data.status").value("QUEUED"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String boardId = objectMapper.readTree(createResponse).path("data").path("id").asString();

        mvc.perform(
                        post(basePath)
                                .with(authentication(ownerAuth))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "requestId",
                                                        "req-scene-1",
                                                        "selection",
                                                        selection,
                                                        "token",
                                                        token,
                                                        "consent",
                                                        true))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(boardId));

        // 동일 requestId에 다른 selection 전송 시 409 충돌
        mvc.perform(
                        post(basePath)
                                .with(authentication(ownerAuth))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "requestId",
                                                        "req-scene-1",
                                                        "selection",
                                                        Map.of(
                                                                "chapterId",
                                                                "ch-1",
                                                                "revision",
                                                                2,
                                                                "start",
                                                                0,
                                                                "end",
                                                                4,
                                                                "settingIds",
                                                                List.of()),
                                                        "token",
                                                        token,
                                                        "consent",
                                                        true))))
                .andExpect(status().isConflict());

        // 워커 실행: 중복 인용("등불이 깨졌다.")은 제외하되 단일 인용("시계가 멈췄다.")이 확인되면 options를 유지
        when(geminiClient.generateScene(anyString()))
                .thenReturn(
                        """
                        {
                          "facts":[
                            {"description":"등불 파손","quote":"등불이 깨졌다."},
                            {"description":"시계 정지","quote":"시계가 멈췄다."}
                          ],
                          "inferences":["누군가 등대에 침입했을 수 있습니다."],
                          "gaps":["시계가 멈춘 시각이 드러나지 않습니다."],
                          "options":["시계가 멈춘 시각을 먼저 보여주고 등불 파편을 뒤에 배치합니다."],
                          "references":[
                            {"id":"doyle-room","question":"시계와 등불 중 어떤 소품을 먼저 노출할까요?"},
                            {"id":"unknown-card","question":"존재하지 않는 카드"}
                          ]
                        }
                        """);
        sceneBoardWorker.process();

        mvc.perform(get(basePath + "/" + boardId).with(authentication(ownerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.facts.length()").value(1))
                .andExpect(jsonPath("$.data.result.facts[0].quote").value("시계가 멈췄다."))
                .andExpect(jsonPath("$.data.result.facts[0].start").value(14))
                .andExpect(jsonPath("$.data.result.facts[0].end").value(22))
                .andExpect(jsonPath("$.data.result.options.length()").value(1))
                .andExpect(jsonPath("$.data.result.references.length()").value(1))
                .andExpect(
                        jsonPath("$.data.result.references[0].reference.id").value("doyle-room"));

        // 존재하지 않는 보드 조회 시 SCENE_BOARD_NOT_FOUND(404)
        mvc.perform(get(basePath + "/missing-board").with(authentication(ownerAuth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCENE_BOARD_NOT_FOUND"));
    }

    @Test
    void sceneImageValidatesReadinessQuotaPngSignatureAndWorkDeletion() throws Exception {
        String boardPath = "/api/work/" + work.getId() + "/scene-board";
        Map<String, Object> selection =
                Map.of(
                        "chapterId",
                        "ch-1",
                        "revision",
                        2,
                        "start",
                        0,
                        "end",
                        4,
                        "settingIds",
                        List.of());
        String previewResponse =
                mvc.perform(
                                post(boardPath + "/preview")
                                        .with(authentication(ownerAuth))
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(selection)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String boardToken =
                objectMapper.readTree(previewResponse).path("data").path("token").asString();

        String createdBoard =
                mvc.perform(
                                post(boardPath)
                                        .with(authentication(ownerAuth))
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "requestId",
                                                                "req-board-img",
                                                                "selection",
                                                                selection,
                                                                "token",
                                                                boardToken,
                                                                "consent",
                                                                true))))
                        .andExpect(status().isAccepted())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String boardId = objectMapper.readTree(createdBoard).path("data").path("id").asString();
        String imageBasePath = boardPath + "/" + boardId + "/images";
        Map<String, Object> imageInput =
                Map.of(
                        "kind",
                        "LOCATION",
                        "confirmed",
                        "비바람이 치는 밤의 등대 내부",
                        "direction",
                        "차갑고 어두운 조명");

        // 보드가 아직 QUEUED 상태일 때 이미지 미리보기는 SCENE_BOARD_NOT_READY(409)
        mvc.perform(
                        post(imageBasePath + "/preview")
                                .with(authentication(ownerAuth))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(imageInput)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCENE_BOARD_NOT_READY"));

        when(geminiClient.generateScene(anyString()))
                .thenReturn(
                        """
                        {"facts":[],"inferences":[],"gaps":[],"options":[],"references":[]}
                        """);
        sceneBoardWorker.process();

        // 보드 완료 후 이미지 미리보기 및 생성 접수
        String imagePreview =
                mvc.perform(
                                post(imageBasePath + "/preview")
                                        .with(authentication(ownerAuth))
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(imageInput)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.revision").value(2))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String imageToken =
                objectMapper.readTree(imagePreview).path("data").path("token").asString();

        String createdImage =
                mvc.perform(
                                post(imageBasePath)
                                        .with(authentication(ownerAuth))
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "requestId",
                                                                "req-img-1",
                                                                "token",
                                                                imageToken,
                                                                "input",
                                                                imageInput,
                                                                "consent",
                                                                true))))
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.data.status").value("QUEUED"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String imageId = objectMapper.readTree(createdImage).path("data").path("id").asString();

        // 일일 한도(1회) 소진 후 새 요청 시 429 및 Retry-After 헤더 반환
        mvc.perform(
                        post(imageBasePath)
                                .with(authentication(ownerAuth))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "requestId",
                                                        "req-img-2",
                                                        "token",
                                                        imageToken,
                                                        "input",
                                                        imageInput,
                                                        "consent",
                                                        true))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.data.resetAt").exists());

        when(geminiClient.generateSceneImage(
                        eq("imagen-3.0-generate-002"), anyString()))
                .thenReturn(VALID_PNG);
        sceneImageWorker.process();

        // 본인만 PNG 바이트 조회 가능, 타 계정은 404
        mvc.perform(get(imageBasePath + "/" + imageId + "/content").with(authentication(otherAuth)))
                .andExpect(status().isNotFound());

        mvc.perform(get(imageBasePath + "/" + imageId + "/content").with(authentication(ownerAuth)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(VALID_PNG));

        // AI 사용량 조회 확인
        String usageResponse =
                mvc.perform(get("/api/ai-usage").with(authentication(ownerAuth)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode usageData = objectMapper.readTree(usageResponse).path("data");
        assertThat(usageData).hasSize(2);

        // 작품 삭제 시 scene_boards와 scene_image_jobs도 모두 정리됨
        Long ownerId = ((LoginUser) ownerAuth.getPrincipal()).id();
        workDeletionService.delete(ownerId, work.getId(), work.getVersion());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM scene_boards WHERE work_id=?",
                                Long.class,
                                work.getId()))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM scene_image_jobs WHERE board_id=?",
                                Long.class,
                                boardId))
                .isZero();
    }
}
