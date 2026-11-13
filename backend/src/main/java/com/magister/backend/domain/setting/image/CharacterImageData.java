package com.magister.backend.domain.setting.image;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

final class CharacterImageData {
    private CharacterImageData() {}

    record Input(@NotBlank @Size(max = 1500) String appearance, @NotNull @Size(max = 500) String direction) {}
    record CreateRequest(@NotNull @Valid Input input, @NotBlank @Size(max = 96) String token,
            @NotBlank @Size(max = 64) String requestId, boolean consent) {}
    record Preview(String prompt, long settingVersion, String token) {}
    record Job(String id, long settingVersion, String status, boolean primary, Instant createdAt) {}
    record Gallery(List<Job> jobs, int remaining, Instant resetAt, int capacity, boolean enabled) {}
}
