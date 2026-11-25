package com.magister.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

class DatabaseConfigurationTests {

    @Test
    void loadsDefaultMariaDbSettingsWithoutConnectingToDatabase() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(
                        applicationContext -> {
                            MutablePropertySources sources =
                                    applicationContext.getEnvironment().getPropertySources();
                            sources.remove(
                                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                            List<String> dotEnvSources = new ArrayList<>();
                            for (PropertySource<?> source : sources) {
                                if (source.getName().contains(".env")) {
                                    dotEnvSources.add(source.getName());
                                }
                            }
                            for (String name : dotEnvSources) {
                                sources.remove(name);
                            }
                        })
                .withPropertyValues("DB_PASSWORD=test-password")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty("spring.datasource.url"))
                                    .isEqualTo("jdbc:mariadb://localhost:3306/magister");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty("spring.datasource.username"))
                                    .isEqualTo("root");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty("spring.datasource.password"))
                                    .isEqualTo("test-password");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty(
                                                            "spring.datasource.driver-class-name"))
                                    .isEqualTo("org.mariadb.jdbc.Driver");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty("spring.jpa.hibernate.ddl-auto"))
                                    .isEqualTo("update");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty(
                                                            "logging.level.org.mariadb.jdbc.message.server.ErrorPacket"))
                                    .isEqualTo("OFF");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty(
                                                            "logging.level.org.hibernate.orm.connections.pooling"))
                                    .isEqualTo("WARN");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty("spring.config.datasource.url"))
                                    .isNull();
                        });
    }

    @Test
    void loadsExternalDatabaseSettingsUnderSpringWithoutConnectingToDatabase() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "DB_URL=jdbc:mariadb://db.example.test:31769/magister",
                        "DB_USERNAME=magister_app",
                        "DB_PASSWORD=test-password",
                        "JPA_DDL_AUTO=validate",
                        "spring.config.import=optional:classpath:/missing-test-config.properties")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty("spring.datasource.url"))
                                    .isEqualTo("jdbc:mariadb://db.example.test:31769/magister");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty("spring.datasource.username"))
                                    .isEqualTo("magister_app");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty("spring.datasource.password"))
                                    .isEqualTo("test-password");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty(
                                                            "spring.datasource.driver-class-name"))
                                    .isEqualTo("org.mariadb.jdbc.Driver");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty("spring.jpa.hibernate.ddl-auto"))
                                    .isEqualTo("validate");
                        });
    }

    @Test
    void loadsProductionCookieAndReverseProxySettings() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "DB_URL=jdbc:mariadb://db.example.test:31769/magister",
                        "DB_USERNAME=magister_app",
                        "DB_PASSWORD=test-password")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty(
                                                            "server.servlet.session.cookie.same-site"))
                                    .isEqualTo("none");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty(
                                                            "server.servlet.session.cookie.secure"))
                                    .isEqualTo("true");
                            assertThat(
                                            context.getEnvironment()
                                                    .getProperty(
                                                            "server.forward-headers-strategy"))
                                    .isEqualTo("native");
                            String internalProxies =
                                    context.getEnvironment()
                                            .getProperty("server.tomcat.remoteip.internal-proxies");
                            assertThat(internalProxies).isNotBlank();
                            Pattern proxyPattern = Pattern.compile(internalProxies);
                            assertThat(proxyPattern.matcher("100.64.0.1").matches()).isTrue();
                            assertThat(proxyPattern.matcher("100.127.255.254").matches()).isTrue();
                            assertThat(proxyPattern.matcher("fd12:3456::1").matches()).isTrue();
                            assertThat(proxyPattern.matcher("203.0.113.10").matches()).isFalse();
                            assertThat(proxyPattern.matcher("100.128.0.1").matches()).isFalse();
                        });
    }
}
