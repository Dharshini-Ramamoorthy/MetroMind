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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
        LoggerFactory.getLogger(GatewayHeaderAuthenticationFilter.class);

    private static final Set<String> ALLOWED_ROLES = Set.of("OC", "MDS", "SADA", "SYSTEM");

    private final String gatewaySecret;

    public GatewayHeaderAuthenticationFilter(
            @Value("${report.security.gateway-secret:}") String gatewaySecret) {
        this.gatewaySecret = gatewaySecret != null ? gatewaySecret.trim() : "";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if ("/actuator/health".equals(path) || "/actuator/info".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!gatewaySecret.isEmpty()) {
            String presentedSecret = request.getHeader("X-Gateway-Secret");
            if (presentedSecret == null || !MessageDigest.isEqual(
                    gatewaySecret.getBytes(StandardCharsets.UTF_8),
                    presentedSecret.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Gateway secret missing or invalid for request to {}", path);
                filterChain.doFilter(request, response);
                return;
            }
        }

        String role = request.getHeader("X-User-Role");
        if (role == null || role.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String cleanRole = role.trim();
        if (cleanRole.toUpperCase(Locale.ENGLISH).startsWith("ROLE_")) {
            cleanRole = cleanRole.substring(5).trim();
        }
        cleanRole = cleanRole.toUpperCase(Locale.ENGLISH);

        if (!ALLOWED_ROLES.contains(cleanRole)) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            userId = "unknown";
        } else {
            userId = userId.trim();
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + cleanRole));
        authorities.add(new SimpleGrantedAuthority(cleanRole));

        var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
