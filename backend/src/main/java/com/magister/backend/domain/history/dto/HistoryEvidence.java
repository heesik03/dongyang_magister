package com.magister.backend.domain.history.dto;

import org.jspecify.annotations.Nullable;

import java.net.URI;

public record HistoryEvidence(String institution, String sourceId, String title,
        URI sourceUrl, Status status, @Nullable String passage) {
    public enum Status { FOUND, NO_PASSAGE }
}
