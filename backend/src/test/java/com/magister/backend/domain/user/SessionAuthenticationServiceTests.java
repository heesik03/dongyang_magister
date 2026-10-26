package com.magister.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.magister.backend.domain.user.dto.LoginRequest;
import com.magister.backend.domain.user.dto.UserResponse;
import com.magister.backend.domain.user.security.AccountPrincipal;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.user.security.SessionAuthenticationService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class SessionAuthenticationServiceTests {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sessionStoresOnlyPublicIdentityFromAuthenticationResult() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        AccountPrincipal account =
                new AccountPrincipal(42L, "writer@example.com", "author", "private-hash");
        when(manager.authenticate(any()))
                .thenReturn(
                        UsernamePasswordAuthenticationToken.authenticated(
                                account, null, account.getAuthorities()));
        SessionAuthenticationService service =
                new SessionAuthenticationService(
                        manager,
                        new HttpSessionSecurityContextRepository(),
                        new ChangeSessionIdAuthenticationStrategy());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserResponse result =
                service.login(
                        new LoginRequest("writer@example.com", "Password1!"), request, response);

        SecurityContext stored =
                (SecurityContext)
                        request.getSession()
                                .getAttribute(
                                        HttpSessionSecurityContextRepository
                                                .SPRING_SECURITY_CONTEXT_KEY);
        assertThat(stored.getAuthentication().getPrincipal())
                .isEqualTo(new LoginUser(42L, "writer@example.com", "author"));
        assertThat(stored.getAuthentication().getCredentials()).isNull();
        assertThat(result).isEqualTo(new UserResponse(42L, "writer@example.com", "author"));
        assertThat(stored.getAuthentication().getPrincipal().toString())
                .doesNotContain("private-hash");
    }
}
