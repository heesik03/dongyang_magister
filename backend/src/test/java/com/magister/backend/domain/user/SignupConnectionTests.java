package com.magister.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.user.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SignupConnectionTests {
    @Autowired MockMvc mvc;
    @Autowired DataSource dataSource;
    @Autowired UserRepository users;
    @MockitoSpyBean PasswordEncoder passwords;

    @Test
    void passwordHashingDoesNotOccupyDatabaseConnectionOrTransaction() throws Exception {
        users.deleteAll();
        AtomicInteger activeDuringHash = new AtomicInteger(-1);
        doAnswer(
                        invocation -> {
                            // HTTP 요청 경로로 확인해야 Open-in-View의 커넥션 점유까지 검출할 수 있다.
                            HikariDataSource pool = (HikariDataSource) dataSource;
                            activeDuringHash.set(pool.getHikariPoolMXBean().getActiveConnections());
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return invocation.callRealMethod();
                        })
                .when(passwords)
                .encode(anyString());

        mvc.perform(
                        post("/api/user")
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"email\":\"connection-test@example.com\",\"name\":\"작가\",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated());
        assertThat(activeDuringHash.get()).as("해싱 중 점유 DB 연결 수").isZero();
        assertThat(users.existsByEmail("connection-test@example.com")).isTrue();
    }
}
