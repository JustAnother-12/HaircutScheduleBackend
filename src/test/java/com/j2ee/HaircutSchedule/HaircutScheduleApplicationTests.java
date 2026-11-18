package com.j2ee.HaircutSchedule;

import com.j2ee.HaircutSchedule.config.PostgresTestConfiguration;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class HaircutScheduleApplicationTests {

	@Test
	void contextLoads() {
	}
}
