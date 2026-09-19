package com.kce.kmrl.approver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.web.client.RestClient;

@SpringBootApplication(scanBasePackages = "com.kce.kmrl.approver")
@EnableMongoRepositories(basePackages = "com.kce.kmrl.approver.repository")
@EnableDiscoveryClient
public class ApproverServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApproverServiceApplication.class, args);
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}