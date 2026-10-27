package com.magister.backend.global.config;

import com.magister.backend.domain.user.security.AuthRequestLimiter;
import com.magister.backend.global.security.AuthRateLimitFilter;
import com.magister.backend.global.security.SecurityResponseHandler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Configuration
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class SecurityConfig {
    @Bean
    FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter(
            AuthRequestLimiter limiter, ObjectMapper objectMapper) {
        FilterRegistrationBean<AuthRateLimitFilter> registration =
                new FilterRegistrationBean<>(new AuthRateLimitFilter(limiter, objectMapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/api/user", "/api/user/login", "/api/user/email/check");
        return registration;
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(
            HttpSessionCsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(
                List.of(
                        new ChangeSessionIdAuthenticationStrategy(),
                        new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
                    List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                allowedOrigins.stream()
                        .map(String::strip)
                        .filter(origin -> !origin.isEmpty())
                        .toList());
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "Accept"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityResponseHandler securityResponseHandler,
            HttpSessionSecurityContextRepository securityContextRepository,
            HttpSessionCsrfTokenRepository csrfTokenRepository)
            throws Exception {
        // CSRF 기본값 유지: /api/user/csrf에서 마스킹된 토큰을 받아 헤더로 전달한다.
        // 저장소를 로그인 처리와 공유해 요청 사이에서 동일한 세션 인증을 복원한다.
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .securityContext(
                        context -> context.securityContextRepository(securityContextRepository))
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
                                errors.authenticationEntryPoint(securityResponseHandler)
                                        .accessDeniedHandler(securityResponseHandler))
                // 기본 LogoutFilter가 CSRF 확인 후 세션 무효화와 인증 컨텍스트 제거를 수행한다.
                .logout(
                        logout ->
                                logout.logoutUrl("/api/user/logout")
                                        .deleteCookies("JSESSIONID")
                                        .logoutSuccessHandler(securityResponseHandler));
        return http.build();
    }
}
