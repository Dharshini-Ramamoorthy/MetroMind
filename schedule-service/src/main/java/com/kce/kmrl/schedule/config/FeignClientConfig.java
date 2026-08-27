package com.kce.kmrl.schedule.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfig {

    @Value("${internal.service-secret:}")
    private String internalServiceSecret;

    @Bean
    public RequestInterceptor internalServiceIdentityInterceptor() {
        return (RequestTemplate template) -> {
            template.header("X-User-Id", "schedule-service");
            template.header("X-User-Role", "SADA");
            if (!internalServiceSecret.isBlank()) {
                template.header("X-Gateway-Secret", internalServiceSecret);
            }
        };
    }
}