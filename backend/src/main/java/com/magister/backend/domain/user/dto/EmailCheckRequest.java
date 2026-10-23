package com.magister.backend.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailCheckRequest(@NotBlank @Email @Size(max = 254) String email) {
    public EmailCheckRequest {
        email = email == null ? null : email.strip();
    }
}
