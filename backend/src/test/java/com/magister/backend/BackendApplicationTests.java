package com.magister.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "gemini.api-key=test-api-key",
        "gemini.model=test-model"
})
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
