package com.magister.backend.domain.user;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.magister.backend.domain.user.repository.UserRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class UserAuthenticationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.magister.backend.domain.gemini.service.GeminiService gemini;

    @BeforeEach
    void clear() {
        users.deleteAll();
    }

    private String signupBody() {
        return "{\"email\":\"Writer@example.com\",\"name\":\"작가\",\"password\":\"Password1!\"}";
    }

    private void signup() throws Exception {
        mvc.perform(
                        post("/api/user")
                                .with(csrf())
                                .contentType("application/json")
                                .content(signupBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void signupRejectsDuplicateAndInvalidPassword() throws Exception {
        signup();
        mvc.perform(
                        post("/api/user")
                                .with(csrf())
                                .contentType("application/json")
                                .content(signupBody()))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/api/user")
                                .with(csrf())
                                .contentType("application/json")
                                .content(signupBody().replace("Password1!", "password1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void realCsrfSessionLoginRotationMeAndLogout() throws Exception {
        signup();
        var csrfResult = mvc.perform(get("/api/user/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String oldId = session.getId();
        String token =
                json.readTree(csrfResult.getResponse().getContentAsString())
                        .path("data")
                        .path("token")
                        .asText();
        mvc.perform(
                        post("/api/user/login")
                                .session(session)
                                .header("X-CSRF-TOKEN", token)
                                .contentType("application/json")
                                .content(
                                        "{\"email\":\"WRITER@example.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(oldId);
        mvc.perform(get("/api/user/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("writer@example.com"));
        mvc.perform(
                        post("/api/gemini")
                                .session(session)
                                .header("X-CSRF-TOKEN", token)
                                .contentType("application/json")
                                .content("{\"prompt\":\"hello\"}"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.when(gemini.generate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.magister.backend.domain.gemini.dto.GeminiResponse("answer"));
        var fresh = mvc.perform(get("/api/user/csrf").session(session)).andReturn();
        String freshToken =
                json.readTree(fresh.getResponse().getContentAsString())
                        .path("data")
                        .path("token")
                        .asText();
        mvc.perform(
                        post("/api/gemini")
                                .session(session)
                                .header("X-CSRF-TOKEN", freshToken)
                                .contentType("application/json")
                                .content("{\"prompt\":\"hello\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/user/logout").session(session).header("X-CSRF-TOKEN", freshToken))
                .andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void deniesMissingCsrfAndUnauthenticatedGeminiAndBadCredentials() throws Exception {
        mvc.perform(post("/api/user").contentType("application/json").content(signupBody()))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/gemini")
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"prompt\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/user/login")
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"email\":\"missing@example.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        signup();
        mvc.perform(
                        post("/api/user/login")
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"email\":\"writer@example.com\",\"password\":\"Wrong1!xx\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void documentsSessionEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/user/logout'].post.summary").value("로그아웃"))
                .andExpect(
                        jsonPath("$.components.securitySchemes.session.name").value("JSESSIONID"));
    }

    @Test
    void checksEmailAvailabilityBeforeSignupAndValidatesRequest() throws Exception {
        mvc.perform(
                        post("/api/user/email/check")
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"email\":\"writer@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
        signup();
        mvc.perform(
                        post("/api/user/email/check")
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"email\":\"WRITER@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
        mvc.perform(
                        post("/api/user/email/check")
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/user/email/check")
                                .contentType("application/json")
                                .content("{\"email\":\"writer@example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsConfiguredCorsOriginAndRejectsDisallowedOrigin() throws Exception {
        mvc.perform(
                        options("/api/user")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "POST")
                                .header(
                                        "Access-Control-Request-Headers",
                                        "Content-Type, X-CSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mvc.perform(
                        options("/api/user")
                                .header("Origin", "https://evil.example.com")
                                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
