package com.magister.backend.global.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;

class GeminiConfigTests {

    @TempDir Path directory;

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(GeminiConfig.class)
                    .withPropertyValues("gemini.timeout=30s");

    @Test
    void importsDotEnvAsPropertiesAndResolvesYamlPlaceholders() throws Exception {
        Path dotenv = directory.resolve(".env");
        Files.writeString(
                dotenv,
                "GEMINI_API_KEY=file-secret\nGEMINI_MODEL=file-model\nGEMINI_TIMEOUT=12s\n");
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.config.import=" + dotenv.toUri() + "[.properties]",
                        "gemini.timeout=${GEMINI_TIMEOUT:30s}")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            GeminiProperties properties = context.getBean(GeminiProperties.class);
                            assertThat(properties.apiKey()).isEqualTo("file-secret");
                            assertThat(properties.model()).isEqualTo("file-model");
                            assertThat(properties.timeout().toSeconds()).isEqualTo(12);
                        });
    }

    @Test
    void bindsSettingsAndCreatesClientWithoutNetworkCall() {
        runner.withPropertyValues("gemini.api-key=test-secret", "gemini.model=test-model")
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(Client.class)
                                    .hasSingleBean(GeminiClient.class);
                            GeminiProperties properties = context.getBean(GeminiProperties.class);
                            assertThat(properties.timeout().toMillis()).isEqualTo(30000);
                            assertThat(properties.toString()).doesNotContain("test-secret");
                        });
    }

    @Test
    void rejectsMissingKey() {
        runner.withPropertyValues("gemini.model=test-model")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMissingModel() {
        runner.withPropertyValues("gemini.api-key=test-secret")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsInvalidTimeouts() {
        for (String timeout : new String[] {"0ms", "-1s", "2147483648ms"}) {
            runner.withPropertyValues(
                            "gemini.api-key=test-secret",
                            "gemini.model=test-model",
                            "gemini.timeout=" + timeout)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
