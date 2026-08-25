package com.emrekiziltoprak.payment.gateway.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"stripe.webhook.secret=whsec_test_dummy",
		"spring.datasource.url=jdbc:h2:mem:payment_gateway",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaymentGatewayServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
