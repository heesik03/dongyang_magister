package com.magister.backend.domain.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:pagination-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.magister.backend.domain.setting.PaginationApiTests$SqlCapture"
        })
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class PaginationApiTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired WorkRepository works;
    @Autowired WorkSettingRepository settings;
    @Autowired TransactionTemplate transaction;
    @Autowired ObjectMapper json;
    private Long ownerId;
    private Long workId;
    private Long secondWorkId;
    private List<Long> characterIds;
    private UsernamePasswordAuthenticationToken owner;

    @BeforeEach
    void prepare() {
        User user =
                users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "author", "hash"));
        ownerId = user.getId();
        owner =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(ownerId, user.getEmail(), user.getName()), null, List.of());
        characterIds = new ArrayList<>();

        transaction.executeWithoutResult(
                status -> {
                    Work work =
                            works.saveAndFlush(
                                    new Work(
                                            users.getReferenceById(ownerId),
                                            "work",
                                            "fantasy",
                                            "",
                                            ""));
                    workId = work.getId();
                    secondWorkId =
                            works.saveAndFlush(
                                            new Work(work.getOwner(), "another", "fantasy", "", ""))
                                    .getId();
                    for (int index = 0; index < 6; index++) {
                        SettingKind kind =
                                index % 2 == 0 ? SettingKind.CHARACTER : SettingKind.WORLD;
                        Long id =
                                settings.saveAndFlush(
                                                new WorkSetting(
                                                        work,
                                                        "rule",
                                                        kind,
                                                        SettingStatus.CONFIRMED,
                                                        "evidence"))
                                        .getId();
                        if (kind == SettingKind.CHARACTER) {
                            characterIds.add(id);
                        }
                    }
                });
        characterIds.sort(Comparator.reverseOrder());
    }

    @Test
    void cursorHasNoCountOrNPlusOneAndIsStableAcrossNewInsertions() throws Exception {
        SqlCapture.QUERIES.get().clear();
        JsonNode first = page(null, "CHARACTER");
        List<String> firstQueries = List.copyOf(SqlCapture.QUERIES.get());
        assertThat(first.path("items").size()).isEqualTo(2);
        assertThat(first.path("items").get(0).path("id").asLong())
                .isEqualTo(characterIds.getFirst());
        assertThat(first.path("hasNext").asBoolean()).isTrue();
        assertThat(firstQueries).hasSize(2).noneMatch(sql -> sql.contains("count("));

        transaction.executeWithoutResult(
                status ->
                        settings.saveAndFlush(
                                new WorkSetting(
                                        works.findById(workId).orElseThrow(),
                                        "new rule",
                                        SettingKind.CHARACTER,
                                        SettingStatus.CONFIRMED,
                                        "evidence")));

        SqlCapture.QUERIES.get().clear();
        JsonNode last = page(first.path("nextCursor").asString(), "CHARACTER");
        assertThat(last.path("items").size()).isEqualTo(1);
        assertThat(last.path("items").get(0).path("id").asLong()).isEqualTo(characterIds.getLast());
        assertThat(last.path("hasNext").asBoolean()).isFalse();
        assertThat(last.path("nextCursor").isNull()).isTrue();
        assertThat(SqlCapture.QUERIES.get()).hasSize(2);
    }

    @Test
    void cursorRejectsOtherFiltersWorksInvalidInputAndUnauthorizedOwner() throws Exception {
        String cursor = page(null, "CHARACTER").path("nextCursor").asString();
        mvc.perform(
                        get(path(workId))
                                .with(authentication(owner))
                                .param("kind", "WORLD")
                                .param("cursor", cursor))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get(path(secondWorkId))
                                .with(authentication(owner))
                                .param("kind", "CHARACTER")
                                .param("cursor", cursor))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path(workId)).with(authentication(owner)).param("cursor", "invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path(workId)).with(authentication(owner)).param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/api/work")
                                .with(authentication(owner))
                                .param("page", "2147483647")
                                .param("size", "100"))
                .andExpect(status().isBadRequest());

        UsernamePasswordAuthenticationToken other =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(-1L, "other@example.com", "other"), null, List.of());
        mvc.perform(get(path(workId)).with(authentication(other))).andExpect(status().isNotFound());
        mvc.perform(get(path(workId))).andExpect(status().isUnauthorized());
    }

    @Test
    void workOffsetQueryCountDoesNotGrowWithPageSize() throws Exception {
        transaction.executeWithoutResult(
                status -> {
                    User user = users.findById(ownerId).orElseThrow();
                    for (int index = 0; index < 20; index++) {
                        works.save(new Work(user, "additional " + index, "fantasy", "", ""));
                    }
                });

        for (int size : List.of(5, 10)) {
            SqlCapture.QUERIES.get().clear();
            mvc.perform(
                            get("/api/work")
                                    .with(authentication(owner))
                                    .param("size", Integer.toString(size)))
                    .andExpect(status().isOk());
            assertThat(SqlCapture.QUERIES.get()).hasSize(2);
        }
    }

    private JsonNode page(String cursor, String kind) throws Exception {
        MockHttpServletRequestBuilder request =
                get(path(workId))
                        .with(authentication(owner))
                        .param("kind", kind)
                        .param("size", "2");
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        String body =
                mvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).path("data");
    }

    private String path(Long id) {
        return "/api/work/" + id + "/setting/scroll";
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
