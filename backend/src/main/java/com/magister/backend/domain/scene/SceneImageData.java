package com.magister.backend.domain.scene;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class SceneImageData {
    private SceneImageData() {}

    enum Kind { LOCATION, PROP }

    record Input(
            @NotNull Kind kind,
            @NotBlank @Size(max = 500) String confirmed,
            @NotNull @Size(max = 500) String direction) {}

    record CreateRequest(
            @NotBlank @Size(max = 64) String requestId,
            @NotBlank @Size(max = 100) String token,
            @NotNull @Valid Input input,
            boolean consent) {}

    record Preview(Input input, String token, long revision) {}
    record Job(String id, String status, Input input, long revision) {}
}
