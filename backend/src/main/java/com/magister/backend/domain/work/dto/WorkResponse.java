package com.magister.backend.domain.work.dto;

import com.magister.backend.domain.work.entity.Work;

import java.time.Instant;

public record WorkResponse(
        Long id,
        String title,
        String genre,
        String era,
        String description,
        Instant createdAt,
        Instant updatedAt,
        Long version) {}
