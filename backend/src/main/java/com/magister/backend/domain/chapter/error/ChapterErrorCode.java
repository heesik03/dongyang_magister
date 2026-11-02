package com.magister.backend.domain.chapter.error;

import com.magister.backend.global.error.code.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChapterErrorCode implements ErrorCode {
    UNSUPPORTED_EXPORT_FORMAT(
            HttpStatus.BAD_REQUEST, "지원하지 않는 내보내기 파일 형식입니다. (txt, md, pdf, docx 지원)"),
    UNSUPPORTED_IMPORT_FORMAT(
            HttpStatus.BAD_REQUEST, "지원하지 않는 가져오기 파일 형식입니다. (txt, md, pdf, docx 지원)"),
    EMPTY_EXPORT_CHAPTERS(HttpStatus.BAD_REQUEST, "내보낼 회차 원고가 없습니다."),
    EMPTY_IMPORT_FILE(HttpStatus.BAD_REQUEST, "비어 있는 파일은 가져올 수 없습니다."),
    MANUSCRIPT_TOO_LONG(HttpStatus.BAD_REQUEST, "원고 본문은 최대 200,000자까지 가져올 수 있습니다."),
    INVALID_MANUSCRIPT_FILE(
            HttpStatus.BAD_REQUEST, "원고 파일을 읽을 수 없습니다. 파일이 손상되었거나 암호화되어 있지 않은지 확인해 주세요."),
    EXPORT_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "원고 파일을 생성하는 중 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
