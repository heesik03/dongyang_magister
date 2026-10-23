package com.magister.backend.global.response;

import com.fasterxml.jackson.annotation.JsonValue;

public interface ResponseCode {
    @JsonValue
    String getCode();
}
