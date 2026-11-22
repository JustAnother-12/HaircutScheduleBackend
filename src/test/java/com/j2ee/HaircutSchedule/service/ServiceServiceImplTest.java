
package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;
import com.j2ee.HaircutSchedule.entity.Service;
import com.j2ee.HaircutSchedule.mapper.ServiceMapper;
import com.j2ee.HaircutSchedule.repository.ServiceRepository;
import com.j2ee.HaircutSchedule.service.impl.ServiceServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.graphql.data.ArgumentValue;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceServiceImplTest {
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private ServiceMapper serviceMapper;
    @InjectMocks
    private ServiceServiceImpl serviceService;

    @Test
    void createService_success() {
        when(serviceRepository.saveAndFlush(any(Service.class)))
                .thenAnswer(invocation -> {
                    Service saved = invocation.getArgument(0);
                    saved.setId(10);
                    return saved;
                });

        when(serviceMapper.toResponse(any(Service.class)))
                .thenAnswer(invocation -> {
                    Service saved = invocation.getArgument(0);

                    return ServiceResponse.builder()
                            .id(saved.getId())
                            .name(saved.getName())
                            .description(saved.getDescription())
                            .price(saved.getPrice())
                            .durationMinutes(saved.getDurationMinutes())
                            .isActive(saved.isActive())
                            .build();
                });

        ServiceResponse result = serviceService.createService(
                "Cắt tóc nam",
                "Cắt tóc cơ bản",
                new BigDecimal("100000"),
                30
        );

        ArgumentCaptor<Service> captor = ArgumentCaptor.forClass(Service.class);

        verify(serviceRepository).saveAndFlush(captor.capture());

        Service saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Cắt tóc nam");
        assertThat(saved.getPrice()).isEqualByComparingTo("100000");
        assertThat(saved.getDurationMinutes()).isEqualTo(30);
        assertThat(saved.isActive()).isTrue();
        assertThat(result.id()).isEqualTo(10);
    }

    @Test
    void updateService_partialUpdate_success() {
        Service existing = Service.builder()
                .id(1)
                .name("Old name")
                .description("Old description")
                .price(new BigDecimal("100000"))
                .durationMinutes(30)
                .isActive(true)
                .build();

        when(serviceRepository.findById(1)).thenReturn(Optional.of(existing));

        when(serviceMapper.toResponse(existing))
                .thenReturn(
                        ServiceResponse.builder()
                                .id(1)
                                .name("New name")
                                .description("Old description")
                                .price(new BigDecimal("100000"))
                                .durationMinutes(30)
                                .isActive(true)
                                .build()
                );

        ServiceResponse result = serviceService.updateService(
                1,
                ArgumentValue.ofNullable("New name"),
                ArgumentValue.omitted(),
                ArgumentValue.omitted(),
                ArgumentValue.omitted(),
                ArgumentValue.omitted()
        );

        assertThat(existing.getName()).isEqualTo("New name");
        assertThat(existing.getDescription()).isEqualTo("Old description");
        assertThat(existing.getPrice()).isEqualByComparingTo("100000");
        assertThat(existing.getDurationMinutes()).isEqualTo(30);
        assertThat(result.name()).isEqualTo("New name");
    }

    @Test
    void createService_negativePrice_throwsException() {
        assertThatThrownBy(() ->
                serviceService.createService(
                        "Cắt tóc",
                        null,
                        new BigDecimal("-1000"),
                        30
                )
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(serviceRepository, serviceMapper);
    }

    @Test
    void updateService_noProvidedFields_throwsException() {
        Service existing = Service.builder()
                .id(1)
                .name("Cắt tóc")
                .description(null)
                .price(new BigDecimal("100000"))
                .durationMinutes(30)
                .isActive(true)
                .build();

        when(serviceRepository.findById(1)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                serviceService.updateService(
                        1,
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted()
                )
        ).isInstanceOf(IllegalArgumentException.class);

        verify(serviceMapper, never()).toResponse(any());
    }
}

