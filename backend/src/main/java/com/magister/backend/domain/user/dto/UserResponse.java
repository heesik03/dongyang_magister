package com.magister.backend.domain.user.dto;

import com.magister.backend.domain.user.entity.User;

public record UserResponse(Long id, String email, String name) {}
