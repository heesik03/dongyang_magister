package com.magister.backend.domain.user.dto;

public record CsrfResponse(String headerName, String token) {}
