package com.magister.backend.domain.work;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.magister.backend.domain.setting.entity.SettingKind;
import com.magister.backend.domain.setting.entity.SettingStatus;
import com.magister.backend.domain.setting.entity.WorkSetting;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

@SpringBootTest(
        properties = "spring.datasource.url=jdbc:h2:mem:work-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WorkApiTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired WorkRepository works;
    @Autowired com.magister.backend.domain.setting.repository.WorkSettingRepository settings;
    @Autowired ObjectMapper json;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transaction;
    private UsernamePasswordAuthenticationToken owner;
    private UsernamePasswordAuthenticationToken other;

    @BeforeEach
    void prepare() {
        settings.deleteAll();
        works.deleteAll();
        User first =
                users.saveAndFlush(
                        new User("owner-" + System.nanoTime() + "@example.com", "작가", "unused"));
        User second =
                users.saveAndFlush(
                        new User("other-" + System.nanoTime() + "@example.com", "다른 작가", "unused"));
        owner =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(first.getId(), first.getEmail(), first.getName()),
                        null,
                        List.of());
        other =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(second.getId(), second.getEmail(), second.getName()),
                        null,
                        List.of());
    }

    private String body() {
        return "{\"title\":\"새 작품\",\"genre\":\"판타지\",\"era\":\"중세\",\"description\":\"소개\"}";
    }

    @Test
    void relationshipsStayConsistentBeforeAndAfterReload() {
        transaction.executeWithoutResult(
                status -> {
                    Long ownerId = ((LoginUser) owner.getPrincipal()).id();
                    User author = users.findById(ownerId).orElseThrow();
                    Work work = new Work(author, "연관관계 검증", "판타지", "중세", "소개");
                    WorkSetting setting =
                            new WorkSetting(
                                    work,
                                    "세계 규칙",
                                    SettingKind.WORLD,
                                    SettingStatus.CONFIRMED,
                                    "마법은 대가를 필요로 한다.");

                    author.addWork(work);
                    work.addSetting(setting);
                    assertSame(author, work.getOwner());
                    assertEquals(List.of(work), author.getWorks());
                    assertSame(work, setting.getWork());
                    assertEquals(List.of(setting), work.getSettings());
                    User differentAuthor =
                            users.findAll().stream()
                                    .filter(candidate -> !candidate.getId().equals(ownerId))
                                    .findFirst()
                                    .orElseThrow();
                    assertThrows(IllegalStateException.class, () -> differentAuthor.addWork(work));
                    Work differentWork = new Work(author, "다른 작품", "판타지", "중세", "소개");
                    assertThrows(
                            IllegalStateException.class, () -> differentWork.addSetting(setting));

                    works.saveAndFlush(work);
                    settings.saveAndFlush(setting);
                    Long workId = work.getId();
                    Long settingId = setting.getId();
                    entityManager.clear();

                    WorkSetting restoredSetting = settings.findById(settingId).orElseThrow();
                    Work restoredWork = restoredSetting.getWork();
                    assertEquals(workId, restoredWork.getId());
                    assertEquals(ownerId, restoredWork.getOwner().getId());
                    assertEquals(List.of(restoredWork), restoredWork.getOwner().getWorks());
                    assertEquals(List.of(restoredSetting), restoredWork.getSettings());
                    status.setRollbackOnly();
                });
    }

    @Test
    void createListUpdateOwnershipAndVersion() throws Exception {
        String result =
                mvc.perform(
                                post("/api/work")
                                        .with(authentication(owner))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .content(body()))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long id = json.readTree(result).path("data").path("id").asLong();
        mvc.perform(get("/api/work").with(authentication(owner)).param("keyword", "작품"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/work/" + id).with(authentication(other)))
                .andExpect(status().isNotFound());
        String update = body().replace("새 작품", "수정된 작품").replace("}", ",\"version\":0}");
        mvc.perform(
                        put("/api/work/" + id)
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(
                        put("/api/work/" + id)
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(update))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/work/" + id).with(authentication(owner)))
                .andExpect(jsonPath("$.data.title").value("수정된 작품"));
    }

    @Test
    void rejectsInvalidAndUnauthenticatedRequests() throws Exception {
        mvc.perform(get("/api/work")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/work").with(authentication(owner)).param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/work")
                                .with(authentication(owner))
                                .contentType("application/json")
                                .content(body()))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/work")
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(body().replace("새 작품", " ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settingsRespectOwnershipScopeFiltersAndVersion() throws Exception {
        long id =
                json.readTree(
                                mvc.perform(
                                                post("/api/work")
                                                        .with(authentication(owner))
                                                        .with(csrf())
                                                        .contentType("application/json")
                                                        .content(body()))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data")
                        .path("id")
                        .asLong();
        String path = "/api/work/" + id + "/setting";
        String settingBody =
                "{\"title\":\"인물"
                    + " 능력\",\"kind\":\"CHARACTER\",\"status\":\"CONFIRMED\",\"content\":\"마법 사용"
                    + " 불가\"}";
        String response =
                mvc.perform(
                                post(path)
                                        .with(authentication(owner))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .content(settingBody))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long settingId = json.readTree(response).path("data").path("id").asLong();
        mvc.perform(get(path).with(authentication(owner)).param("status", "IDEA"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get(path + "/" + settingId).with(authentication(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get(path).with(authentication(other))).andExpect(status().isNotFound());
        String update = settingBody.replace("불가", "가능").replace("}", ",\"version\":0}");
        mvc.perform(
                        put(path + "/" + settingId)
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(
                        put(path + "/" + settingId)
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(update))
                .andExpect(status().isConflict());
        mvc.perform(
                        post(path)
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(settingBody.replace("CHARACTER", "UNKNOWN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).with(authentication(owner)).param("size", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.paths['/api/work/{workId}/setting'].post.responses['201']")
                                .exists())
                .andExpect(jsonPath("$.paths['/api/user'].post.responses['409']").exists());
    }

    @Test
    void settingCannotBeAccessedThroughAnotherOwnedWork() throws Exception {
        long firstId =
                json.readTree(
                                mvc.perform(
                                                post("/api/work")
                                                        .with(authentication(owner))
                                                        .with(csrf())
                                                        .contentType("application/json")
                                                        .content(body()))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data")
                        .path("id")
                        .asLong();
        long secondId =
                json.readTree(
                                mvc.perform(
                                                post("/api/work")
                                                        .with(authentication(owner))
                                                        .with(csrf())
                                                        .contentType("application/json")
                                                        .content(body()))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data")
                        .path("id")
                        .asLong();
        String payload =
                "{\"title\":\"규칙\",\"kind\":\"WORLD\",\"status\":\"IDEA\",\"content\":\"내용\"}";
        long settingId =
                json.readTree(
                                mvc.perform(
                                                post("/api/work/" + firstId + "/setting")
                                                        .with(authentication(owner))
                                                        .with(csrf())
                                                        .contentType("application/json")
                                                        .content(payload))
                                        .andExpect(status().isCreated())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data")
                        .path("id")
                        .asLong();
        mvc.perform(
                        get("/api/work/" + secondId + "/setting/" + settingId)
                                .with(authentication(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/work/" + firstId + "/setting")
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(payload.replace("규칙", " ")))
                .andExpect(status().isBadRequest());
    }
}
