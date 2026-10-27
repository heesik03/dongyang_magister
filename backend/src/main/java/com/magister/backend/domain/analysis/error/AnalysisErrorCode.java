package com.magister.backend.domain.analysis.error;

import com.magister.backend.global.error.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AnalysisErrorCode implements ErrorCode {
    PREVIEW_REQUIRED(HttpStatus.BAD_REQUEST, "전송 자료를 미리 확인하고 동의해 주세요."),
    PREVIEW_CHANGED(HttpStatus.CONFLICT, "전송 자료나 확인 유효기간이 바뀌었습니다. 미리보기를 다시 확인해 주세요."),
    SNAPSHOT_UNAVAILABLE(HttpStatus.CONFLICT, "기존 작업의 입력 자료를 복원할 수 없습니다. 현재 원고로 새 검토를 준비해 주세요.");
    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
