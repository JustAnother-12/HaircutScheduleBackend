
package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;
import com.j2ee.HaircutSchedule.entity.Service;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.mapper.ServiceMapper;
import com.j2ee.HaircutSchedule.repository.ServiceRepository;
import com.j2ee.HaircutSchedule.service.impl.ServiceServiceImpl;

import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

    private ServiceResponse mapService(Service service) {
        return ServiceResponse.builder()
                .id(service.getId())
                .name(service.getName())
                .description(service.getDescription())
                .price(service.getPrice())
                .durationMinutes(service.getDurationMinutes())
                .isActive(service.isActive())
                .build();
    }

    private void stubEmptySearchPage() {
        when(serviceRepository.findAll(
                ArgumentMatchers.<Specification<Service>>any(),
                any(Pageable.class)
        )).thenReturn(Page.empty(PageRequest.of(0, 10)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Specification<Service> captureSpecification() {
        ArgumentCaptor<Specification<Service>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(Specification.class);

        verify(serviceRepository)
                .findAll(captor.capture(), any(Pageable.class));

        return captor.getValue();
    }

    private static class CriteriaMocks {

        private final Root<Service> root = mock(Root.class);
        private final CriteriaBuilder builder = mock(CriteriaBuilder.class);
        private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
        private final Predicate predicate = mock(Predicate.class);

        CriteriaMocks() {
            when(builder.conjunction()).thenReturn(predicate);

            when(builder.and(
                    any(Predicate.class),
                    any(Predicate.class)
            )).thenReturn(predicate);
        }

        void evaluate(Specification<Service> specification) {
            specification.toPredicate(root, query, builder);
        }
    }

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

    @Test
    void getServiceById_activeService_success() {
        // Arrange
        Service service = Service.builder()
                .id(1)
                .name("Haircut")
                .description("Basic haircut")
                .price(new BigDecimal("100000"))
                .durationMinutes(30)
                .isActive(true)
                .build();

        ServiceResponse expected = mapService(service);

        when(serviceRepository.findByIdAndIsActive(1, true))
                .thenReturn(Optional.of(service));

        when(serviceMapper.toResponse(service))
                .thenReturn(expected);

        // Execute
        ServiceResponse result = serviceService.getServiceById(1, true);

        // Assert
        assertThat(result).isEqualTo(expected);
        assertThat(result.isActive()).isTrue();

        verify(serviceRepository).findByIdAndIsActive(1, true);
        verify(serviceRepository, never()).findById(1);
        verify(serviceMapper).toResponse(service);
    }

    @Test
    void getServiceById_inactivePublicService_notFound() {
        // Arrange
        when(serviceRepository.findByIdAndIsActive(2, true))
                .thenReturn(Optional.empty());

        // Execute & Assert
        assertThatThrownBy(() ->
                serviceService.getServiceById(2, true)
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cannot find Service with id: 2");

        verify(serviceRepository).findByIdAndIsActive(2, true);
        verify(serviceMapper, never()).toResponse(any(Service.class));
    }

    @Test
    void getServiceById_adminCanGetInactiveService() {
        // Arrange
        Service inactiveService = Service.builder()
                .id(3)
                .name("Old haircut")
                .description("Inactive service")
                .price(new BigDecimal("80000"))
                .durationMinutes(25)
                .isActive(false)
                .build();

        when(serviceRepository.findById(3))
                .thenReturn(Optional.of(inactiveService));

        when(serviceMapper.toResponse(inactiveService))
                .thenReturn(mapService(inactiveService));

        // Execute
        ServiceResponse result =
                serviceService.getServiceById(3, false);

        // Assert
        assertThat(result.id()).isEqualTo(3);
        assertThat(result.isActive()).isFalse();

        verify(serviceRepository).findById(3);
        verify(serviceRepository, never())
                .findByIdAndIsActive(anyInt(), anyBoolean());
    }

    @Test
    void getServiceById_missingService_notFound() {
        // Arrange
        when(serviceRepository.findById(999))
                .thenReturn(Optional.empty());

        // Execute & Assert
        assertThatThrownBy(() ->
                serviceService.getServiceById(999, false)
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cannot find Service with id: 999");

        verify(serviceRepository).findById(999);
        verifyNoInteractions(serviceMapper);
    }

    @Test
    void getPublicServices_filtersByNamePriceAndDuration() {
        // Arrange
        BigDecimal minPrice = new BigDecimal("50000");
        BigDecimal maxPrice = new BigDecimal("150000");

        stubEmptySearchPage();

        // Execute
        serviceService.getPublicServices(
                1,
                10,
                "CUT",
                minPrice,
                maxPrice,
                20,
                45
        );

        // Capture Specification
        Specification<Service> specification = captureSpecification();

        CriteriaMocks criteria = new CriteriaMocks();

        Path<Boolean> activePath = mock(Path.class);
        Path<String> namePath = mock(Path.class);
        Expression<String> lowerName = mock(Expression.class);
        Path<BigDecimal> pricePath = mock(Path.class);
        Path<Integer> durationPath = mock(Path.class);

        when(criteria.root.<Boolean>get("isActive"))
                .thenReturn(activePath);

        when(criteria.builder.equal(activePath, true))
                .thenReturn(criteria.predicate);

        when(criteria.root.<String>get("name"))
                .thenReturn(namePath);

        when(criteria.builder.lower(namePath))
                .thenReturn(lowerName);

        when(criteria.builder.like(lowerName, "%cut%"))
                .thenReturn(criteria.predicate);

        when(criteria.root.<BigDecimal>get("price"))
                .thenReturn(pricePath);

        when(criteria.builder.greaterThanOrEqualTo(pricePath, minPrice))
                .thenReturn(criteria.predicate);

        when(criteria.builder.lessThanOrEqualTo(pricePath, maxPrice))
                .thenReturn(criteria.predicate);

        when(criteria.root.<Integer>get("durationMinutes"))
                .thenReturn(durationPath);

        when(criteria.builder.greaterThanOrEqualTo(durationPath, 20))
                .thenReturn(criteria.predicate);

        when(criteria.builder.lessThanOrEqualTo(durationPath, 45))
                .thenReturn(criteria.predicate);

        // Evaluate Specification
        criteria.evaluate(specification);

        // Assert
        verify(criteria.builder).equal(activePath, true);
        verify(criteria.builder).like(lowerName, "%cut%");

        verify(criteria.builder)
                .greaterThanOrEqualTo(pricePath, minPrice);

        verify(criteria.builder)
                .lessThanOrEqualTo(pricePath, maxPrice);

        verify(criteria.builder)
                .greaterThanOrEqualTo(durationPath, 20);

        verify(criteria.builder)
                .lessThanOrEqualTo(durationPath, 45);
    }

    @Test
    void getPublicServices_filterNameCaseInsensitive() {
        // Arrange
        stubEmptySearchPage();

        // Execute
        serviceService.getPublicServices(
                1,
                10,
                "  CuT  ",
                null,
                null,
                null,
                null
        );

        Specification<Service> specification = captureSpecification();

        CriteriaMocks criteria = new CriteriaMocks();

        Path<Boolean> activePath = mock(Path.class);
        Path<String> namePath = mock(Path.class);
        Expression<String> lowerName = mock(Expression.class);

        when(criteria.root.<Boolean>get("isActive"))
                .thenReturn(activePath);

        when(criteria.builder.equal(activePath, true))
                .thenReturn(criteria.predicate);

        when(criteria.root.<String>get("name"))
                .thenReturn(namePath);

        when(criteria.builder.lower(namePath))
                .thenReturn(lowerName);

        when(criteria.builder.like(lowerName, "%cut%"))
                .thenReturn(criteria.predicate);

        // Execute Specification
        criteria.evaluate(specification);

        // Assert
        verify(criteria.builder).lower(namePath);
        verify(criteria.builder).like(lowerName, "%cut%");
    }

    @Test
    void getAdminServices_filterIsActiveFalse() {
        // Arrange
        stubEmptySearchPage();

        // Execute
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

        Specification<Service> specification = captureSpecification();

        CriteriaMocks criteria = new CriteriaMocks();

        Path<Boolean> activePath = mock(Path.class);

        when(criteria.root.<Boolean>get("isActive"))
                .thenReturn(activePath);

        when(criteria.builder.equal(activePath, false))
                .thenReturn(criteria.predicate);

        // Execute Specification
        criteria.evaluate(specification);

        // Assert
        verify(criteria.builder).equal(activePath, false);
    }

    @Test
    void getPublicServices_filterByMinAndMaxPrice() {
        // Arrange
        BigDecimal minPrice = new BigDecimal("50000");
        BigDecimal maxPrice = new BigDecimal("200000");

        stubEmptySearchPage();

        // Execute
        serviceService.getPublicServices(
                1,
                10,
                null,
                minPrice,
                maxPrice,
                null,
                null
        );

        Specification<Service> specification = captureSpecification();

        CriteriaMocks criteria = new CriteriaMocks();

        Path<Boolean> activePath = mock(Path.class);
        Path<BigDecimal> pricePath = mock(Path.class);

        when(criteria.root.<Boolean>get("isActive"))
                .thenReturn(activePath);

        when(criteria.builder.equal(activePath, true))
                .thenReturn(criteria.predicate);

        when(criteria.root.<BigDecimal>get("price"))
                .thenReturn(pricePath);

        when(criteria.builder.greaterThanOrEqualTo(pricePath, minPrice))
                .thenReturn(criteria.predicate);

        when(criteria.builder.lessThanOrEqualTo(pricePath, maxPrice))
                .thenReturn(criteria.predicate);

        // Execute Specification
        criteria.evaluate(specification);

        // Assert
        verify(criteria.builder)
                .greaterThanOrEqualTo(pricePath, minPrice);

        verify(criteria.builder)
                .lessThanOrEqualTo(pricePath, maxPrice);
    }

    @Test
    void getPublicServices_filterByDuration() {
        // Arrange
        Integer minDuration = 20;
        Integer maxDuration = 60;

        stubEmptySearchPage();

        // Execute
        serviceService.getPublicServices(
                1,
                10,
                null,
                null,
                null,
                minDuration,
                maxDuration
        );

        Specification<Service> specification = captureSpecification();

        CriteriaMocks criteria = new CriteriaMocks();

        Path<Boolean> activePath = mock(Path.class);
        Path<Integer> durationPath = mock(Path.class);

        when(criteria.root.<Boolean>get("isActive"))
                .thenReturn(activePath);

        when(criteria.builder.equal(activePath, true))
                .thenReturn(criteria.predicate);

        when(criteria.root.<Integer>get("durationMinutes"))
                .thenReturn(durationPath);

        when(criteria.builder.greaterThanOrEqualTo(durationPath, minDuration))
                .thenReturn(criteria.predicate);

        when(criteria.builder.lessThanOrEqualTo(durationPath, maxDuration))
                .thenReturn(criteria.predicate);

        // Execute Specification
        criteria.evaluate(specification);

        // Assert
        verify(criteria.builder)
                .greaterThanOrEqualTo(durationPath, minDuration);

        verify(criteria.builder)
                .lessThanOrEqualTo(durationPath, maxDuration);
    }

    @Test
    void getAdminServices_invalidPriceRange_rejected() {
        // Execute & Assert
        assertThatThrownBy(() ->
                serviceService.getAdminServices(
                        1,
                        10,
                        null,
                        new BigDecimal("200000"),
                        new BigDecimal("100000"),
                        null,
                        null,
                        null
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minPrice cannot be greater than maxPrice");

        verifyNoInteractions(serviceRepository, serviceMapper);
    }

    @Test
    void getAdminServices_invalidDurationRange_rejected() {
        // Execute & Assert
        assertThatThrownBy(() ->
                serviceService.getAdminServices(
                        1,
                        10,
                        null,
                        null,
                        null,
                        60,
                        30,
                        null
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minDurationMinutes cannot exceed maxDurationMinutes");

        verifyNoInteractions(serviceRepository, serviceMapper);
    }

    @Test
    void createService_zeroPrice_success() {
        // Arrange
        when(serviceRepository.saveAndFlush(any(Service.class)))
                .thenAnswer(invocation -> {
                    Service service = invocation.getArgument(0);
                    service.setId(10);
                    return service;
                });

        when(serviceMapper.toResponse(any(Service.class)))
                .thenAnswer(invocation ->
                        mapService(invocation.getArgument(0))
                );

        // Execute
        ServiceResponse result = serviceService.createService(
                "Free consultation",
                "Free service",
                BigDecimal.ZERO,
                30
        );

        // Assert
        ArgumentCaptor<Service> captor =
                ArgumentCaptor.forClass(Service.class);

        verify(serviceRepository).saveAndFlush(captor.capture());

        Service saved = captor.getValue();

        assertThat(saved.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getName()).isEqualTo("Free consultation");
        assertThat(saved.getDurationMinutes()).isEqualTo(30);
        assertThat(saved.isActive()).isTrue();

        assertThat(result.id()).isEqualTo(10);
    }

    @Test
    void createService_invalidDuration_rejected() {
        // Execute & Assert
        assertThatThrownBy(() ->
                serviceService.createService(
                        "Haircut",
                        "Basic haircut",
                        new BigDecimal("100000"),
                        0
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duration must be greater than zero");

        verifyNoInteractions(serviceRepository, serviceMapper);
    }

    @Test
    void createService_invalidName_rejected() {
        // Execute & Assert
        assertThatThrownBy(() ->
                serviceService.createService(
                        "   ",
                        "Basic haircut",
                        new BigDecimal("100000"),
                        30
                )
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(serviceRepository, serviceMapper);
    }

    @Test
    void createService_descriptionTooLong_rejected() {
        // Arrange
        String longDescription = "a".repeat(501);

        // Execute & Assert
        assertThatThrownBy(() ->
                serviceService.createService(
                        "Haircut",
                        longDescription,
                        new BigDecimal("100000"),
                        30
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Description cannot exceed 500 characters");

        verifyNoInteractions(serviceRepository, serviceMapper);
    }

    @Test
    void updateService_updatePriceAndDuration_success() {
        // Arrange
        Service existing = Service.builder()
                .id(1)
                .name("Haircut")
                .description("Basic haircut")
                .price(new BigDecimal("100000"))
                .durationMinutes(30)
                .isActive(true)
                .build();

        when(serviceRepository.findById(1))
                .thenReturn(Optional.of(existing));

        when(serviceMapper.toResponse(existing))
                .thenAnswer(invocation ->
                        mapService(invocation.getArgument(0))
                );

        // Execute
        ServiceResponse result = serviceService.updateService(
                1,
                ArgumentValue.omitted(),
                ArgumentValue.omitted(),
                ArgumentValue.ofNullable(new BigDecimal("150000")),
                ArgumentValue.ofNullable(45),
                ArgumentValue.omitted()
        );

        // Assert
        assertThat(existing.getName()).isEqualTo("Haircut");
        assertThat(existing.getDescription()).isEqualTo("Basic haircut");
        assertThat(existing.getPrice())
                .isEqualByComparingTo("150000");
        assertThat(existing.getDurationMinutes()).isEqualTo(45);

        assertThat(result.price()).isEqualByComparingTo("150000");
        assertThat(result.durationMinutes()).isEqualTo(45);

        verify(serviceMapper).toResponse(existing);
    }

    @Test
    void updateService_clearDescription_success() {
        // Arrange
        Service existing = Service.builder()
                .id(1)
                .name("Haircut")
                .description("Old description")
                .price(new BigDecimal("100000"))
                .durationMinutes(30)
                .isActive(true)
                .build();

        when(serviceRepository.findById(1))
                .thenReturn(Optional.of(existing));

        when(serviceMapper.toResponse(existing))
                .thenAnswer(invocation ->
                        mapService(invocation.getArgument(0))
                );

        // Execute
        ServiceResponse result = serviceService.updateService(
                1,
                ArgumentValue.omitted(),
                ArgumentValue.<String>ofNullable(null),
                ArgumentValue.omitted(),
                ArgumentValue.omitted(),
                ArgumentValue.omitted()
        );

        // Assert
        assertThat(existing.getDescription()).isNull();
        assertThat(result.description()).isNull();

        verify(serviceMapper).toResponse(existing);
    }

    @Test
    void updateService_explicitNullForPrice_rejected() {
        // Arrange
        Service existing = Service.builder()
                .id(1)
                .name("Haircut")
                .description("Basic haircut")
                .price(new BigDecimal("100000"))
                .durationMinutes(30)
                .isActive(true)
                .build();

        when(serviceRepository.findById(1))
                .thenReturn(Optional.of(existing));

        // Execute & Assert
        assertThatThrownBy(() ->
                serviceService.updateService(
                        1,
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.<BigDecimal>ofNullable(null),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price must be greater than or equal to zero");

        // Price must remain unchanged.
        assertThat(existing.getPrice())
                .isEqualByComparingTo("100000");

        verify(serviceMapper, never()).toResponse(any(Service.class));
    }

    @Test
    void updateService_serviceNotFound_throwsException() {
        // Arrange
        when(serviceRepository.findById(999))
                .thenReturn(Optional.empty());

        // Execute & Assert
        assertThatThrownBy(() ->
                serviceService.updateService(
                        999,
                        ArgumentValue.ofNullable("New name"),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted()
                )
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cannot find Service with id: 999");

        verify(serviceMapper, never()).toResponse(any(Service.class));
    }
}

