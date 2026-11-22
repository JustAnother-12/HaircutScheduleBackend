package com.j2ee.HaircutSchedule.integration;

import com.j2ee.HaircutSchedule.config.PostgresTestConfiguration;
import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.service.UserService;
import com.j2ee.HaircutSchedule.support.TestDataFactory;
import com.j2ee.HaircutSchedule.support.TestFixtureConfiguration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import({
        PostgresTestConfiguration.class,
        TestFixtureConfiguration.class
})
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TestDataFactory fixtures;

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminUsers_shouldIncludeInactiveUsersWhenNoActiveFilter() {
        var active = fixtures.customerA();

        var inactive = fixtures.saveUser(
                "Inactive customer",
                "inactive@example.com",
                com.j2ee.HaircutSchedule.enums.UserRole.CUSTOMER,
                false
        );

        var result = userService.getUsers(
                1,
                10,
                null,
                null,
                null,
                null
        );

        assertThat(result.items())
                .extracting(UserResponse::id)
                .contains(active.getId(), inactive.getId());
    }

    @Test
    void publicBarbers_shouldRespectOptionalActiveFilter() {
        var active = fixtures.barberA();

        var inactive = fixtures.saveUser(
                "Inactive barber",
                "inactive-barber@example.com",
                com.j2ee.HaircutSchedule.enums.UserRole.BARBER,
                false
        );

        var result = userService.getBarbers(
                1,
                10,
                null,
                null,
                null,
                null
        );

        assertThat(result.items())
                .extracting(UserResponse::id)
                .contains(active.getId(), inactive.getId());
    }
}
