package com.kce.kmrl.security;

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
import java.util.List;
import java.util.Set;

/**
 * Same gateway header auth pattern as maintenance-service.
 * The approver panel only allows SADA (System Admin) role.
 */
@Component
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayHeaderAuthenticationFilter.class);
    private static final Set<String> VALID_ROLES = Set.of("OC", "MDS", "SADA");

    private final String gatewaySecret;

    public GatewayHeaderAuthenticationFilter(
            @Value("${approver.security.gateway-secret:}") String gatewaySecret) {
        this.gatewaySecret = gatewaySecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        if (!gatewaySecret.isBlank()) {
            String presented = request.getHeader("X-Gateway-Secret");
            if (!gatewaySecret.equals(presented)) {
                log.warn("Rejected request to {} - missing/incorrect X-Gateway-Secret", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }
        }

        String userId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");

        if (role != null && VALID_ROLES.contains(role.toUpperCase())) {
            List<GrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId != null ? userId : "unknown", null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
