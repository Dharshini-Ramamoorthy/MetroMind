package com.kce.kmrl.alert.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate(
            RestTemplateBuilder builder,
            @Value("${alert.integration.caller-user-id:alert-service}") String callerUserId,
            @Value("${alert.integration.caller-role:SADA}") String callerRole,
            @Value("${alert.security.gateway-secret:}") String gatewaySecret) {

        ClientHttpRequestInterceptor identityInterceptor = (request, body, execution) -> {
            request.getHeaders().add("X-User-Id", callerUserId);
            request.getHeaders().set("X-User-Role", callerRole);
            if (gatewaySecret != null && !gatewaySecret.isBlank()) {
                request.getHeaders().set("X-Gateway-Secret", gatewaySecret);
            }
            return execution.execute(request, body);
        };

        return builder
            .setConnectTimeout(Duration.ofSeconds(3))
            .setReadTimeout(Duration.ofSeconds(5))
            .additionalInterceptors(identityInterceptor)
            .build();
    }
}
