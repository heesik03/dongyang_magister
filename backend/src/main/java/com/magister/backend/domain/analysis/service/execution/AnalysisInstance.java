package com.magister.backend.domain.analysis.service.execution;

import org.springframework.stereotype.Component;
import java.util.UUID;

/** 프로세스가 재시작될 때 바뀌며 다른 서버의 정상 작업과 구분한다. */
@Component
public class AnalysisInstance {
    private final String id = UUID.randomUUID().toString();

    public String id() {
        return id;
    }
}
