package com.magister.backend.domain.user.dto;

import jakarta.validation.constraints.*;

public record SignupRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 30) String name,
        @NotBlank
                @Size(min = 8, max = 128)
                @Pattern(
                        regexp = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^\\p{L}\\p{N}\\s])\\S+$",
                        message = "비밀번호는 영문·숫자·특수문자를 포함하고 공백 없이 입력해 주세요.")
                String password) {
    public SignupRequest {
        email = email == null ? null : email.strip();
        name = name == null ? null : name.strip();
    }
}
