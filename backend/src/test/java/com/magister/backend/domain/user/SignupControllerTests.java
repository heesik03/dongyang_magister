package com.magister.backend.domain.user;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.user.controller.UserController;
import com.magister.backend.domain.user.service.UserService;
import com.magister.backend.global.error.GlobalExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SignupControllerTests {
    @Test
    void rejectsPasswordsWithoutAllRequiredCharacterGroups() throws Exception {
        var service = mock(UserService.class);
        var mvc =
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
}
