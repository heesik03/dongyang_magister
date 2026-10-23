package com.magister.backend.domain.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.magister.backend.domain.user.dto.SignupRequest;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.service.UserService;
import com.magister.backend.global.error.exception.CustomException;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

class UserServiceTests {
    @Test
    void hashesPasswordAndNormalizesEmail() {
        var repository = mock(UserRepository.class);
        var encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        when(repository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));
        new UserService(repository, encoder)
                .signup(new SignupRequest("Writer@Example.com", "작가", "Password1!"));
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("writer@example.com");
        assertThat(encoder.matches("Password1!", captor.getValue().getPasswordHash())).isTrue();
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("Password1!");
    }

    @Test
    void rejectsDuplicateEmail() {
        var repository = mock(UserRepository.class);
        when(repository.existsByEmail("writer@example.com")).thenReturn(true);
        var service =
                new UserService(repository, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        assertThatThrownBy(
                        () ->
                                service.signup(
                                        new SignupRequest(
                                                "writer@example.com", "작가", "Password1!")))
                .isInstanceOf(CustomException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
