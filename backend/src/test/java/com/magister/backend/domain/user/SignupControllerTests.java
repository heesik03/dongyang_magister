package com.magister.backend.domain.user;

import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.user.controller.UserController;
import com.magister.backend.domain.user.dto.SignupRequest;
import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.service.UserService;
import com.magister.backend.global.error.GlobalExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SignupControllerTests {
    @Test
    void rejectsPasswordsWithoutAllRequiredCharacterGroups() throws Exception {
        UserService service = mock(UserService.class);
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new UserController(service, null))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        for (String password :
                new String[] {"password1", "Password!", "12345678!", "Ab1!", "Pass word1!"}) {
            mvc.perform(
                            post("/api/user")
                                    .contentType("application/json")
                                    .content(
                                            "{\"email\":\"writer@example.com\",\"name\":\"writer\",\"password\":\""
                                                    + password
                                                    + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void stripsSurroundingWhitespaceBeforeValidation() throws Exception {
        UserService service = mock(UserService.class);
        when(service.signup(any())).thenReturn(new UserResponse(1L, "writer@example.com", "작가"));
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new UserController(service, null))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        mvc.perform(
                        post("/api/user")
                                .contentType("application/json")
                                .content(
                                        "{\"email\":\"  writer@example.com  \",\"name\":\"  작가 "
                                                + " \",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated());

        verify(service).signup(new SignupRequest("writer@example.com", "작가", "Password1!"));
    }
}
