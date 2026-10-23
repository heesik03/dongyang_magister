package com.magister.backend.global.config;

import com.magister.backend.global.security.SecurityResponseHandler;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
public class SecurityConfig {
    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService users, PasswordEncoder passwords) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }

    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityResponseHandler responses,
            HttpSessionSecurityContextRepository contexts)
            throws Exception {
        // CSRF 기본값 유지: /api/user/csrf에서 마스킹된 토큰을 받아 헤더로 전달한다.
        // 저장소를 로그인 처리와 공유해 요청 사이에서 동일한 세션 인증을 복원한다.
        http.securityContext(context -> context.securityContextRepository(contexts))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                HttpMethod.POST,
                                                "/api/user",
                                                "/api/user/login",
                                                "/api/user/email/check")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/user/csrf")
                                        .permitAll()
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(responses)
                                        .accessDeniedHandler(responses))
                // 기본 LogoutFilter가 CSRF 확인 후 세션 무효화와 인증 컨텍스트 제거를 수행한다.
                .logout(
                        logout ->
                                logout.logoutUrl("/api/user/logout")
                                        .deleteCookies("JSESSIONID")
                                        .logoutSuccessHandler(responses));
        return http.build();
    }
}
