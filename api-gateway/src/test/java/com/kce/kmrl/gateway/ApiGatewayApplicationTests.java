package com.kce.kmrl.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250655368566D5971",
		"GATEWAY_INTERNAL_SECRET=test-gateway-internal-secret"
})
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}

