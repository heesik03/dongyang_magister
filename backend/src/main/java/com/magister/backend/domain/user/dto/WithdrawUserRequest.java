package com.magister.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawUserRequest(
        @NotBlank @Size(max = 128) String currentPassword, boolean agreed) {}
