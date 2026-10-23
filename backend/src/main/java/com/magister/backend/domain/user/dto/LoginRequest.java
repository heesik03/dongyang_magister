package com.magister.backend.domain.user.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 128) String password) {
    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
