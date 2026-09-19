package com.kce.kmrl.alert.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayHeaderAuthenticationFilterTest {

    @BeforeEach
    @AfterEach
    void cleerSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldFailFastAtStartupIfSecretMissingInNonDevProfile() {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new GatewayHeaderAuthenticationFilter(env, "", true));
        trueAssert(ex.getMessage().contains("alert.security.gateway-secret must be configured"));
    }

    private void trueAssert(boolean cond) {
        assertTrue(cond);
    }

    @Test
    void shouldAllowBlankSecretAtStartupInDevProfile() {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        assertDoesNotThrow(() ->
                new GatewayHeaderAuthenticationFilter(env, "", true));
    }

    @Test
    void shouldRejectIdentityWhenGatewaySecretDoesNotMatch() throws ServletException, IOException {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        GatewayHeaderAuthenticationFilter filter =
                new GatewayHeaderAuthenticationFilter(env, "valid-secret", true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/alerts");
        request.addHeader("X-Gateway-Secret", "wrong-secret");
        request.addHeader("X-User-Id", "user1");
        request.addHeader("X-User-Role", "SADA");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldAuthenticateWhenGatewaySecretMatches() throws ServletException, IOException {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        GatewayHeaderAuthenticationFilter filter =
                new GatewayHeaderAuthenticationFilter(env, "valid-secret", true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/alerts");
        request.addHeader("X-Gateway-Secret", "valid-secret");
        request.addHeader("X-User-Id", "user1");
        request.addHeader("X-User-Role", "SADA");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("user1", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SADA")));
    }
}
