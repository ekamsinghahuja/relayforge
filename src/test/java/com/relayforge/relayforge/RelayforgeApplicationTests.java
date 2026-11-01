package com.relayforge.relayforge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "relayforge.delivery.enabled=false")
class RelayforgeApplicationTests {

	@Test
	void contextLoads() {
	}

}
