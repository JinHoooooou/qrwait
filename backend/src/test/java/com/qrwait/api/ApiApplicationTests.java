package com.qrwait.api;

import com.qrwait.api.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApiApplicationTests extends IntegrationTestSupport {

	@Test
	void contextLoads() {
	}

}
