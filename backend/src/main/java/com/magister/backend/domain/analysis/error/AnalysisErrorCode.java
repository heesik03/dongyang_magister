package com.magister.backend.domain.analysis.error;

import com.magister.backend.global.error.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AnalysisErrorCode implements ErrorCode {
    HISTORY_FINDING_NOT_FOUND(HttpStatus.NOT_FOUND, "역사 검토 항목을 찾을 수 없습니다."),
    HISTORY_DECISION_STALE(HttpStatus.CONFLICT, "원고 또는 근거가 변경되었습니다. 다시 검토해 주세요."),
    HISTORY_SIZE_LIMIT(HttpStatus.BAD_REQUEST, "역사 고증은 회차 원고 6,000자까지 지원합니다."),
    PREVIEW_REQUIRED(HttpStatus.BAD_REQUEST, "전송 자료를 미리 확인하고 동의해 주세요."),
    PREVIEW_CHANGED(HttpStatus.CONFLICT, "전송 자료나 확인 유효기간이 바뀌었습니다. 미리보기를 다시 확인해 주세요."),
    COMPARISON_NOT_READY(HttpStatus.CONFLICT, "완료된 분석 결과만 비교할 수 있습니다."),
    COMPARISON_INVALID_BASE(HttpStatus.BAD_REQUEST, "같은 회차의 이전 완료 분석을 선택해 주세요."),
    SNAPSHOT_UNAVAILABLE(HttpStatus.CONFLICT, "기존 작업의 입력 자료를 복원할 수 없습니다. 현재 원고로 새 검토를 준비해 주세요.");
    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
