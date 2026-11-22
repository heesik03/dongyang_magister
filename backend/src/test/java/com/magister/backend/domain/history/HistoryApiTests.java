package com.magister.backend.domain.history;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "ENCYKOREA_API_KEY=")
@AutoConfigureMockMvc
class HistoryApiTests {
    @Autowired MockMvc mvc;

    @Test
    void documentsBothHistoryOperations() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/history/{provider}/candidates'].get").exists())
                .andExpect(jsonPath("$.paths['/api/history/{provider}/evidence/{sourceId}'].get").exists());
    }

    @Test
    void requiresLoginAndValidInput() throws Exception {
        String path = "/api/history/ENCYCLOPEDIA/candidates";
        mvc.perform(get(path).param("q", "경복궁")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("writer")).param("q", " "))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/history/HERITAGE/evidence/invalid").with(user("writer")).param("keyword", "1395"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsMissingEncyclopediaKey() throws Exception {
        mvc.perform(get("/api/history/ENCYCLOPEDIA/candidates").with(user("writer")).param("q", "경복궁"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }
}
