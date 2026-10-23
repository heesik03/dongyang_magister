package com.magister.backend.global.error.code;

import com.magister.backend.global.response.ResponseCode;
import org.springframework.http.HttpStatusCode;

public interface ErrorCode extends ResponseCode {
    HttpStatusCode getHttpStatus();
    String getMessage();
}
