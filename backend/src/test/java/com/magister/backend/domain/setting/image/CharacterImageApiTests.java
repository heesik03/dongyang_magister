package com.magister.backend.domain.setting.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.setting.repository.WorkSettingRepository;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;
import com.magister.backend.global.gemini.GeminiClient;
import com.magister.backend.global.gemini.GeminiClient.GeneratedPortrait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:character-image;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "character-image.poll-delay-ms=3600000"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CharacterImageApiTests {
    private static final Map<String, String> INPUT = Map.of("appearance", "검은 머리", "direction", "수채화");
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jG1sAAAAASUVORK5CYII=");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired WorkRepository workRepository;
    @Autowired WorkSettingRepository settingRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CharacterImageWorker worker;
    @MockitoBean GeminiClient geminiClient;

    private UsernamePasswordAuthenticationToken owner;
    private Work work;
    private WorkSetting character;
    private String path;

    @BeforeEach
    void prepare() {
        jdbcTemplate.update("DELETE FROM character_image_jobs");
        User user = userRepository.saveAndFlush(new User(
                "portrait-" + System.nanoTime() + "@example.com", "작가", "unused"));
        owner = authenticationFor(user);
        work = workRepository.saveAndFlush(new Work(user, "작품", "판타지", "", ""));
        character = settingRepository.saveAndFlush(new WorkSetting(
                work, "인물", SettingKind.CHARACTER, SettingStatus.CONFIRMED, "외형: 검은 머리"));
        path = imagePath(work, character);
    }

    @Test
    void verifiesConsentDeduplicatesAndPreservesPrimaryAfterFailureAndQuota() throws Exception {
        JsonNode preview = data(preview(path));
        String token = preview.get("token").asText();
        String prompt = preview.get("prompt").asText();
        create(path, token, "first", false, INPUT).andExpect(status().isBadRequest());
        create(path, token, "first", true, Map.of("appearance", "다른 외형", "direction", ""))
                .andExpect(status().isConflict());
        String first = data(create(path, token, "first", true, INPUT)
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("QUEUED")))
                .get("id").asText();
        create(path, token, "first", true, INPUT)
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.id").value(first));
        when(geminiClient.generatePortrait(eq("gemini-3.1-flash-image"), anyString()))
                .thenReturn(new GeneratedPortrait("image/png", PNG))
                .thenThrow(new IllegalStateException("synthetic failure"));
        worker.process();
        verify(geminiClient).generatePortrait("gemini-3.1-flash-image", prompt);
        mvc.perform(put(path + "/" + first + "/primary").with(authentication(owner)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(path + "/" + first + "/content").with(authentication(owner)))
                .andExpect(status().isOk()).andExpect(content().bytes(PNG));

        String second = data(create(path, token, "second", true, INPUT)
                .andExpect(status().isAccepted())).get("id").asText();
        worker.process();
        mvc.perform(put(path + "/" + second + "/primary").with(authentication(owner)).with(csrf()))
                .andExpect(status().isNotFound());
        create(path, token, "third", true, INPUT).andExpect(status().isAccepted());
        create(path, token, "fourth", true, INPUT).andExpect(status().isTooManyRequests());
        mvc.perform(get(path).with(authentication(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.remaining").value(0))
                .andExpect(jsonPath("$.data.capacity").value(10));
        assertEquals("FAILED", jdbcTemplate.queryForObject(
                "SELECT status FROM character_image_jobs WHERE id=?", String.class, second));
        assertEquals(first, jdbcTemplate.queryForObject(
                "SELECT id FROM character_image_jobs WHERE is_primary=TRUE AND setting_id=?",
                String.class, character.getId()));
    }

    @Test
    void rejectsStalePreviewAndChecksCapacityBeforeChargingQuota() throws Exception {
        String staleToken = data(preview(path)).get("token").asText();
        character.update("인물", SettingKind.CHARACTER, SettingStatus.CONFIRMED, "외형: 새 설정");
        character = settingRepository.saveAndFlush(character);
        create(path, staleToken, "stale", true, INPUT).andExpect(status().isConflict());
        String token = data(preview(path)).get("token").asText();
        for (int index = 0; index < 10; index++) {
            jdbcTemplate.update("""
                    INSERT INTO character_image_jobs
                        (id, setting_id, setting_version, request_id, request_fingerprint, prompt, model, status, created_at)
                    VALUES (?, ?, 0, ?, 'fixture', '', 'test', 'FAILED', CURRENT_TIMESTAMP)
                    """, UUID.randomUUID().toString(), character.getId(), "past-" + index);
        }
        create(path, token, "capacity", true, INPUT).andExpect(status().isConflict());
        mvc.perform(get(path).with(authentication(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.remaining").value(3))
                .andExpect(jsonPath("$.data.jobs.length()").value(10));
    }

    @Test
    void rejectsUnownedAndMismatchedSettingsAndCleansUpOnBothDeletionPaths() throws Exception {
        User stranger = userRepository.saveAndFlush(
                new User("stranger-" + System.nanoTime() + "@example.com", "다른 작가", "unused"));
        mvc.perform(get(path).with(authentication(authenticationFor(stranger)))).andExpect(status().isNotFound());
        mvc.perform(post(path + "/preview").with(authentication(owner))
                .contentType("application/json").content(objectMapper.writeValueAsString(INPUT)))
                .andExpect(status().isForbidden());
        Work anotherWork = workRepository.saveAndFlush(
                new Work(work.getOwner(), "다른 작품", "판타지", "", ""));
        mvc.perform(get(imagePath(anotherWork, character)).with(authentication(owner)))
                .andExpect(status().isNotFound());
        WorkSetting secondCharacter = settingRepository.saveAndFlush(new WorkSetting(
                work, "두 번째 인물", SettingKind.CHARACTER, SettingStatus.CONFIRMED, "인물 설정"));
        String secondPath = imagePath(work, secondCharacter);
        create(path, data(preview(path)).get("token").asText(), "delete-character", true, INPUT)
                .andExpect(status().isAccepted());
        create(secondPath, data(preview(secondPath)).get("token").asText(), "delete-work", true, INPUT)
                .andExpect(status().isAccepted());
        mvc.perform(delete("/api/work/{workId}/setting/{id}", work.getId(), character.getId())
                .with(authentication(owner)).with(csrf()).param("version", character.getVersion().toString()))
                .andExpect(status().isNoContent());
        assertEquals(0, countJobs(character));
        mvc.perform(delete("/api/work/{workId}", work.getId()).with(authentication(owner)).with(csrf())
                .param("version", workRepository.findById(work.getId()).orElseThrow().getVersion().toString()))
                .andExpect(status().isNoContent());
        assertEquals(0, countJobs(secondCharacter));
    }

    private int countJobs(WorkSetting setting) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM character_image_jobs WHERE setting_id=?", Integer.class, setting.getId());
    }

    private UsernamePasswordAuthenticationToken authenticationFor(User user) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new LoginUser(user.getId(), user.getEmail(), user.getName()), null, List.of());
    }

    private String imagePath(Work work, WorkSetting setting) {
        return "/api/work/" + work.getId() + "/setting/" + setting.getId() + "/character-image";
    }

    private ResultActions preview(String path) throws Exception {
        return mvc.perform(post(path + "/preview").with(authentication(owner)).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(INPUT)))
                .andExpect(status().isOk());
    }

    private ResultActions create(String path, String token, String requestId, boolean consent,
            Map<String, String> input) throws Exception {
        return mvc.perform(post(path).with(authentication(owner)).with(csrf()).contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "input", input, "token", token, "requestId", requestId, "consent", consent))));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }
}
