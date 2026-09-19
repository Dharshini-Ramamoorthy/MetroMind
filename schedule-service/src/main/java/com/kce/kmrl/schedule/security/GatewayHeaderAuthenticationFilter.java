package com.kce.kmrl.schedule.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Component
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
        LoggerFactory.getLogger(GatewayHeaderAuthenticationFilter.class);

    private final String gatewaySecret;

    public GatewayHeaderAuthenticationFilter(
            @Value("${schedule.security.gateway-secret:}") String gatewaySecret) {
        if (gatewaySecret == null || gatewaySecret.isBlank()) {
            throw new IllegalStateException("schedule.security.gateway-secret must be configured and non-blank");
        }
        this.gatewaySecret = gatewaySecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String presented = request.getHeader("X-Gateway-Secret");
        if (presented == null || !MessageDigest.isEqual(
                gatewaySecret.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rejected request to {} - missing/incorrect X-Gateway-Secret " +
                    "(request did not come through api-gateway)", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or incorrect gateway secret");
            return;
        }

        String userId = request.getHeader("X-User-Id");
        String role   = request.getHeader("X-User-Role");

        if (role == null || role.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing user role");
            return;
        }
        if (userId == null || userId.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing user identity");
            return;
        }

        String cleanRole = role.trim().toUpperCase().replace("ROLE_", "");
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + cleanRole));
            var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}

