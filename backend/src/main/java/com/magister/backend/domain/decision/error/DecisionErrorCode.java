package com.magister.backend.domain.decision.error;

import com.magister.backend.global.error.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DecisionErrorCode implements ErrorCode {
    CONFLICT(HttpStatus.CONFLICT, "DECISION_CONFLICT", "다른 탭에서 판단이 변경되었습니다. 최신 판단을 확인해 주세요."),
    BASIS_CHANGED(
            HttpStatus.CONFLICT,
            "DECISION_BASIS_CHANGED",
            "근거가 변경되었습니다. 인용 위치를 다시 확인하거나 재분석해 주세요.");
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
