package com.magister.backend.global.response;

public enum SuccessCode implements ResponseCode {
    SUCCESS;

    @Override
    public String getCode() {
        return name();
    }
}
