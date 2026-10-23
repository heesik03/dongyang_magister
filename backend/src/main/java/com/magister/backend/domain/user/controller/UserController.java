package com.magister.backend.domain.user.controller;

import com.magister.backend.domain.user.dto.*;
import com.magister.backend.domain.user.service.UserService;
import com.magister.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService users;

    @Operation(summary = "회원가입")
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.created(URI.create("/api/user/me"))
                .body(ApiResponse.success(users.signup(request)));
    }
}
