package com.magister.backend.domain.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@ActiveProfiles("test")
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:scene-board-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "scene-board.analysis-daily-limit=2",
            "scene-board.poll-delay-ms=3600000"
        })
@AutoConfigureMockMvc
class SceneBoardTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkRepository workRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private WorkSettingRepository workSettingRepository;
    @Autowired private WorkDeletionService workDeletionService;
    @Autowired private SceneBoardWorker sceneBoardWorker;
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

        mvc.perform(get("/api/ai-usage").with(authentication(ownerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].feature").value("SCENE_ANALYSIS"));

        // 존재하지 않는 보드 조회 시 SCENE_BOARD_NOT_FOUND(404)
        mvc.perform(get(basePath + "/missing-board").with(authentication(ownerAuth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCENE_BOARD_NOT_FOUND"));

        mvc.perform(get(basePath + "/" + boardId + "/images").with(authentication(ownerAuth)))
                .andExpect(status().isNotFound());

        Long ownerId = ((LoginUser) ownerAuth.getPrincipal()).id();
        workDeletionService.delete(ownerId, work.getId(), work.getVersion());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM scene_boards WHERE work_id=?",
                                Long.class,
                                work.getId()))
                .isZero();
    }
}

