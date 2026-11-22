package com.j2ee.HaircutSchedule.controller;

import com.j2ee.HaircutSchedule.config.GraphQLConfig;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;
import com.j2ee.HaircutSchedule.service.ServiceService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.graphql.test.tester.GraphQlTester;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@GraphQlTest(
        com.j2ee.HaircutSchedule.controller.graphql.ServiceController.class
)
@Import(GraphQLConfig.class)
class ServiceControllerGraphQlTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private ServiceService serviceService;

    @Test
    void servicesQuery_returnsPage() {
        ServiceResponse item = ServiceResponse.builder()
                .id(1)
                .name("Cắt tóc nam")
                .price(new BigDecimal("100000"))
                .durationMinutes(30)
                .isActive(true)
                .build();

        PageResponse<ServiceResponse> page =
                new PageResponse<>(
                        List.of(item),
                        new PageResponse.PageInfo(
                                1,
                                1,
                                5,
                                1
                        )
                );

        when(serviceService.getPublicServices(
                1,
                5,
                "Cắt",
                null,
                null,
                null,
                null
        )).thenReturn(page);

        graphQlTester.document("""
                query {
                    services(
                        page: 1,
                        limit: 5,
                        name: "Cắt"
                    ) {
                        items {
                            id
                            name
                            price
                            durationMinutes
                        }
                        pageInfo {
                            total
                            page
                            limit
                            pages
                        }
                    }
                }
                """)
                .execute()
                .path("services.items[0].name")
                .entity(String.class)
                .isEqualTo("Cắt tóc nam");

        verify(serviceService).getPublicServices(
                1,
                5,
                "Cắt",
                null,
                null,
                null,
                null
        );
    }

    @Test
    void createServiceMutation_acceptsInputObject() {
        ServiceResponse response = ServiceResponse.builder()
                .id(10)
                .name("Cắt tóc nam")
                .price(new BigDecimal("100000"))
                .durationMinutes(30)
                .isActive(true)
                .build();

        when(serviceService.createService(
                "Cắt tóc nam",
                null,
                new BigDecimal("100000"),
                30
        )).thenReturn(response);

        graphQlTester.document("""
                mutation {
                    createService(input: {
                        name: "Cắt tóc nam"
                        price: 100000
                        durationMinutes: 30
                    }) {
                        id
                        name
                        isActive
                    }
                }
                """)
                .execute()
                .path("createService.name")
                .entity(String.class)
                .isEqualTo("Cắt tóc nam");

        verify(serviceService).createService(
                "Cắt tóc nam",
                null,
                new BigDecimal("100000"),
                30
        );
    }

    @Test
    void createServiceMutation_missingRequiredPrice_returnsGraphQlError() {
        var result = graphQlTester.document("""
                mutation {
                    createService(input: {
                        name: "Cắt tóc nam"
                        durationMinutes: 30
                    }) {
                        id
                    }
                }
                """).execute();

        result.errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());

        verifyNoInteractions(serviceService);
    }

    @Test
    void createServiceMutation_serviceRejectsInput_returnsGraphQlError() {
        when(serviceService.createService(
                "Invalid service",
                null,
                new BigDecimal("-100"),
                30
        )).thenThrow(
                new IllegalArgumentException("Price cannot be negative")
        );

        var result = graphQlTester.document("""
                mutation {
                    createService(input: {
                        name: "Invalid service"
                        price: -100
                        durationMinutes: 30
                    }) {
                        id
                    }
                }
                """).execute();

        result.errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }
}
