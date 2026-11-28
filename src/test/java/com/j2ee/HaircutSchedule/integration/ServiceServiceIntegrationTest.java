package com.j2ee.HaircutSchedule.integration;

import com.j2ee.HaircutSchedule.config.PostgresTestConfiguration;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;
import com.j2ee.HaircutSchedule.entity.Service;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.repository.ServiceRepository;
import com.j2ee.HaircutSchedule.service.ServiceService;
import com.j2ee.HaircutSchedule.support.TestDataFactory;
import com.j2ee.HaircutSchedule.support.TestFixtureConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

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
    private ServiceRepository serviceRepository;

    @Autowired
    private TestDataFactory fixtures;

    @PersistenceContext
    private EntityManager entityManager;

    private PageResponse<ServiceResponse> publicSearch(
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minDuration,
            Integer maxDuration,
            int page,
            int limit
    ) {
        return serviceService.getPublicServices(
                page, limit, name, minPrice, maxPrice, minDuration, maxDuration
        );
    }

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

    @Test
    void publicServices_filtersNameCaseInsensitive() {
        // Arrange
        fixtures.saveService("Classic Haircut", "A", new BigDecimal("80000"), 30, true);
        fixtures.saveService("Hair Coloring", "B", new BigDecimal("200000"), 90, true);
        fixtures.saveService("Manicure", "C", new BigDecimal("70000"), 25, true);

        // Execute
        PageResponse<ServiceResponse> result = publicSearch(
                "hAiRcUt", null, null, null, null, 1, 10
        );

        // Assert
        assertThat(result.items())
                .extracting(ServiceResponse::name)
                .containsExactly("Classic Haircut");
        assertThat(result.pageInfo().total()).isEqualTo(1);
    }

    @Test
    void publicServices_filtersPriceRange() {
        // Arrange
        fixtures.saveService("Price 80k", null, new BigDecimal("80000"), 30, true);
        fixtures.saveService("Price 100k", null, new BigDecimal("100000"), 30, true);
        fixtures.saveService("Price 120k", null, new BigDecimal("120000"), 30, true);

        // Execute
        PageResponse<ServiceResponse> result = publicSearch(
                null, new BigDecimal("90000"), new BigDecimal("110000"), null, null, 1, 10
        );

        // Assert
        assertThat(result.items())
                .extracting(ServiceResponse::name)
                .containsExactly("Price 100k");
        assertThat(result.pageInfo().total()).isEqualTo(1);
    }

    @Test
    void publicServices_combinedFilters() {
        // Arrange
        Service matching = fixtures.saveService(
                "Premium Haircut", null, new BigDecimal("120000"), 45, true
        );
        fixtures.saveService("Premium Color", null, new BigDecimal("120000"), 45, true);
        fixtures.saveService("Premium Haircut", null, new BigDecimal("80000"), 45, true);
        fixtures.saveService("Premium Haircut", null, new BigDecimal("120000"), 20, true);
        fixtures.saveService("Premium Haircut", null, new BigDecimal("120000"), 45, false);

        // Execute
        PageResponse<ServiceResponse> result = publicSearch(
                "HAIRCUT", new BigDecimal("100000"), new BigDecimal("150000"),
                30, 60, 1, 10
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(matching.getId());
        assertThat(result.pageInfo().total()).isEqualTo(1);
    }

    @Test
    void publicServices_secondPage_returnsCorrectItems() {
        // Arrange
        for (int i = 1; i <= 12; i++) {
            fixtures.saveService(
                    String.format("Paged Service %02d", i), null,
                    new BigDecimal("50000"), 30, true
            );
        }

        // Execute
        PageResponse<ServiceResponse> firstPage = publicSearch(
                null, null, null, null, null, 1, 10
        );
        PageResponse<ServiceResponse> secondPage = publicSearch(
                null, null, null, null, null, 2, 10
        );

        // Assert
        assertThat(firstPage.items()).hasSize(10);
        assertThat(secondPage.items()).hasSize(2);
        assertThat(firstPage.pageInfo().total()).isEqualTo(12);
        assertThat(firstPage.pageInfo().pages()).isEqualTo(2);
        assertThat(secondPage.pageInfo().page()).isEqualTo(2);

        Set<Integer> firstIds = new HashSet<>(
                firstPage.items().stream().map(ServiceResponse::id).toList()
        );
        Set<Integer> secondIds = new HashSet<>(
                secondPage.items().stream().map(ServiceResponse::id).toList()
        );
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminServices_filterInactiveFalse() {
        // Arrange
        fixtures.serviceA();
        Service inactive = fixtures.inactiveService();

        // Execute
        PageResponse<ServiceResponse> result = serviceService.getAdminServices(
                1, 10, null, null, null, null, null, false
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(inactive.getId());
        assertThat(result.items()).allMatch(item -> !item.isActive());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminServices_combinedFilters() {
        // Arrange
        Service matching = fixtures.saveService(
                "Admin Premium Cut", null, new BigDecimal("120000"), 45, true
        );
        fixtures.saveService("Admin Premium Color", null, new BigDecimal("120000"), 45, true);
        fixtures.saveService("Admin Premium Cut", null, new BigDecimal("80000"), 45, true);
        fixtures.saveService("Admin Premium Cut", null, new BigDecimal("120000"), 20, true);
        fixtures.saveService("Admin Premium Cut", null, new BigDecimal("120000"), 45, false);

        // Execute
        PageResponse<ServiceResponse> result = serviceService.getAdminServices(
                1, 10, "PREMIUM CUT", new BigDecimal("100000"),
                new BigDecimal("150000"), 30, 60, true
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(matching.getId());
        assertThat(result.items().get(0).isActive()).isTrue();
        assertThat(result.pageInfo().total()).isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminServiceCanReadInactiveService() {
        // Arrange
        Service inactive = fixtures.inactiveService();

        // Execute
        ServiceResponse adminResult = serviceService.getServiceById(inactive.getId(), false);

        // Assert
        assertThat(adminResult.id()).isEqualTo(inactive.getId());
        assertThat(adminResult.isActive()).isFalse();
        assertThatThrownBy(() -> serviceService.getServiceById(inactive.getId(), true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void publicServices_filtersDurationRange() {
        // Arrange
        fixtures.saveService("Duration 20", null, new BigDecimal("50000"), 20, true);
        fixtures.saveService("Duration 30", null, new BigDecimal("50000"), 30, true);
        fixtures.saveService("Duration 45", null, new BigDecimal("50000"), 45, true);

        // Execute
        PageResponse<ServiceResponse> result = publicSearch(
                null, null, null, 25, 45, 1, 10
        );

        // Assert
        assertThat(result.items())
                .extracting(ServiceResponse::name)
                .containsExactly("Duration 30", "Duration 45");
        assertThat(result.pageInfo().total()).isEqualTo(2);
    }

    @Test
    void publicServices_pageBeyondLastPage_returnsEmptyItems() {
        // Arrange
        fixtures.saveService("Beyond 1", null, new BigDecimal("50000"), 30, true);
        fixtures.saveService("Beyond 2", null, new BigDecimal("50000"), 30, true);
        fixtures.saveService("Beyond 3", null, new BigDecimal("50000"), 30, true);

        // Execute
        PageResponse<ServiceResponse> result = publicSearch(
                null, null, null, null, null, 3, 2
        );

        // Assert
        assertThat(result.items()).isEmpty();
        assertThat(result.pageInfo().total()).isEqualTo(3);
        assertThat(result.pageInfo().page()).isEqualTo(3);
        assertThat(result.pageInfo().pages()).isEqualTo(2);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminServices_invalidMinMaxFilter_rejected() {
        // Arrange
        BigDecimal minGreaterThanMax = new BigDecimal("150000");
        BigDecimal maxLessThanMin = new BigDecimal("100000");

        // Execute & Assert: invalid price range
        assertThatThrownBy(() -> serviceService.getAdminServices(
                1, 10, null, minGreaterThanMax, maxLessThanMin,
                null, null, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minPrice cannot be greater than maxPrice");

        // Execute & Assert: invalid duration range
        assertThatThrownBy(() -> serviceService.getAdminServices(
                1, 10, null, null, null, 60, 30, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minDurationMinutes cannot exceed maxDurationMinutes");
    }
}
