package com.j2ee.HaircutSchedule.integration;

import com.j2ee.HaircutSchedule.config.PostgresTestConfiguration;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;
import com.j2ee.HaircutSchedule.service.ServiceService;
import com.j2ee.HaircutSchedule.support.TestDataFactory;
import com.j2ee.HaircutSchedule.support.TestFixtureConfiguration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
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
class ServiceServiceIntegrationTest {

    @Autowired
    private ServiceService serviceService;

    @Autowired
    private TestDataFactory fixtures;

    @Test
    void publicServices_onlyReturnsActiveServices() {
        fixtures.serviceA();
        fixtures.inactiveService();

        PageResponse<ServiceResponse> result =
                serviceService.getPublicServices(
                        1,
                        10,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        assertThat(result.items())
                .hasSize(1);

        assertThat(result.items().get(0).name())
                .isEqualTo("Cắt tóc nam");

        assertThat(result.pageInfo().total())
                .isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminServices_canFindInactiveServices() {
        fixtures.serviceA();
        fixtures.inactiveService();

        PageResponse<ServiceResponse> result =
                serviceService.getAdminServices(
                        1,
                        10,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false
                );

        assertThat(result.items())
                .hasSize(1);

        assertThat(result.items().get(0).isActive())
                .isFalse();

        assertThat(result.items().get(0).name())
                .isEqualTo("Cắt tóc trẻ em");
    }

    @Test
    void publicServices_invalidPage_throwsException() {
        assertThatThrownBy(() ->
                serviceService.getPublicServices(
                        0,
                        10,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(authorities = "CUSTOMER")
    void customerCannotFetchAdminServices() {
        assertThatThrownBy(() ->
                serviceService.getAdminServices(
                        1,
                        10,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ).isInstanceOf(AccessDeniedException.class);
    }
}
