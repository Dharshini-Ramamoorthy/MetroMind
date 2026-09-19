package com.kce.kmrl.alert.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;

@Component
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
        LoggerFactory.getLogger(GatewayHeaderAuthenticationFilter.class);

    private static final Set<String> VALID_ROLES = Set.of("OC", "MDS", "SADA");

    private final String gatewaySecret;
    private final boolean requireGatewaySecret;

    public GatewayHeaderAuthenticationFilter(
            Environment environment,
            @Value("${alert.security.gateway-secret:}") String gatewaySecret,
            @Value("${alert.security.require-gateway-secret:true}") boolean requireGatewaySecret) {
        this.gatewaySecret = gatewaySecret != null ? gatewaySecret : "";
        this.requireGatewaySecret = requireGatewaySecret;

        boolean isDevOrTest = environment.acceptsProfiles(Profiles.of("dev", "test"));
        if (this.requireGatewaySecret && !isDevOrTest) {
            if (this.gatewaySecret.isBlank()) {
                throw new IllegalStateException(
                    "alert.security.gateway-secret must be configured and non-blank in non-dev profiles when require-gateway-secret is true");
            }
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (requireGatewaySecret && gatewaySecret.isBlank()) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Gateway secret is not configured");
            return;
        }
        if (requireGatewaySecret || !gatewaySecret.isBlank()) {
            String presented = request.getHeader("X-Gateway-Secret");
            if (presented == null || !MessageDigest.isEqual(
                    gatewaySecret.getBytes(StandardCharsets.UTF_8),
                    presented.getBytes(StandardCharsets.UTF_8))) {

                log.warn("Ignoring identity headers on {} – missing/incorrect X-Gateway-Secret " +
                    "(request did not come through api-gateway)", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }
        }


        String userId = request.getHeader("X-User-Id");
        String role   = request.getHeader("X-User-Role");

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
