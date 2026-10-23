package com.magister.backend.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConfigurationTests {

    @Test
    void loadsDatabaseSettingsUnderSpringWithoutConnectingToDatabase() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("DB_PASSWORD=test-password",
                        "spring.config.import=optional:classpath:/missing-test-config.properties")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:mysql://localhost:3306/magister");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.password"))
                            .isEqualTo("test-password");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.driver-class-name"))
                            .isEqualTo("com.mysql.cj.jdbc.Driver");
                    assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                            .isEqualTo("validate");
                    assertThat(context.getEnvironment().getProperty("spring.config.datasource.url"))
                            .isNull();
                });
    }
}
