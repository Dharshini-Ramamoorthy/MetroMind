package com.kce.kmrl.fleet.security;

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

@Component
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
        LoggerFactory.getLogger(GatewayHeaderAuthenticationFilter.class);

    @Value("${fleet.security.gateway-secret:}")
    private String gatewaySecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (gatewaySecret != null && !gatewaySecret.isBlank()) {
            String presented = request.getHeader("X-Gateway-Secret");
            if (!gatewaySecret.equals(presented)) {
                log.warn("Unauthorized access attempt to {}: invalid or missing gateway secret", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Missing or incorrect gateway secret\"}");
                return;
            }
        }

        String userId = request.getHeader("X-User-Id");
        String role   = request.getHeader("X-User-Role");

        if (role == null || role.isBlank() || userId == null || userId.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Missing gateway caller identity.\"}");
            return;
        }

        String cleanRole = role.trim().toUpperCase().replace("ROLE_", "");
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + cleanRole);

        var authentication = new UsernamePasswordAuthenticationToken(
                userId.trim(), null, java.util.List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}

