package com.magister.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.magister.backend.domain.user.dto.SignupRequest;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserConstraintTranslator;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.service.UserService;
import com.magister.backend.global.error.exception.CustomException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

class UserServiceTests {
    @Test
    void onlyKnownEmailUniqueConstraintIsTranslated() {
        assertThat(
                        UserConstraintTranslator.isDuplicateEmail(
                                new DataIntegrityViolationException(
                                        "Duplicate entry for key 'users.uk_users_email'")))
                .isTrue();
        assertThat(
                        UserConstraintTranslator.isDuplicateEmail(
                                new DataIntegrityViolationException(
                                        "Unique index violation PUBLIC.UK_USERS_EMAIL_INDEX_4")))
                .isTrue();
        assertThat(
                        UserConstraintTranslator.isDuplicateEmail(
                                new DataIntegrityViolationException(
                                        "Duplicate email for unique alternate_email")))
                .isFalse();
    }

    @Test
    void hashesPasswordAndNormalizesEmail() {
        UserRepository repository = mock(UserRepository.class);
        Pbkdf2PasswordEncoder encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        when(repository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));
        new UserService(repository, encoder)
                .signup(new SignupRequest("Writer@Example.com", "작가", "Password1!"));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("writer@example.com");
        assertThat(encoder.matches("Password1!", captor.getValue().getPasswordHash())).isTrue();
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("Password1!");
    }

    @Test
    void rejectsDuplicateEmail() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.existsByEmail("writer@example.com")).thenReturn(true);
        UserService service =
                new UserService(repository, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        assertThatThrownBy(
                        () ->
                                service.signup(
                                        new SignupRequest(
                                                "writer@example.com", "작가", "Password1!")))
                .isInstanceOf(CustomException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rethrowsNonEmailIntegrityViolationInsteadOfTreatingAsDuplicateEmail() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.existsByEmail("writer@example.com")).thenReturn(false);
        when(repository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Data too long for column 'name'"));
        UserService service =
                new UserService(repository, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());

        assertThatThrownBy(
                        () ->
                                service.signup(
                                        new SignupRequest(
                                                "writer@example.com", "작가", "Password1!")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
