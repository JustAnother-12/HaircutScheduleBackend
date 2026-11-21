package com.j2ee.HaircutSchedule.service.impl;

import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.mapper.ServiceMapper;
import com.j2ee.HaircutSchedule.repository.ServiceRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.j2ee.HaircutSchedule.service.ServiceService;
import com.j2ee.HaircutSchedule.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.j2ee.HaircutSchedule.util.Helpers.validateArgumentStringNotBlankOrNull;


@Service
@RequiredArgsConstructor
public class ServiceServiceImpl implements ServiceService {
    private final ServiceRepository serviceRepository;
    private final ServiceMapper serviceMapper;

    @Override
    public List<ServiceResponse> getAllServices(boolean activeOnly) {
        List<com.j2ee.HaircutSchedule.entity.Service> services;

        if (activeOnly) {
            services = serviceRepository.findByIsActiveTrueOrderByNameAsc();
        } else {
            services = serviceRepository.findAllByOrderByNameAsc();
        }

        return services.stream()
                .map(serviceMapper::toResponse)
                .toList();
    }

    @Override
    public ServiceResponse getServiceById(Integer id, boolean activeOnly) {
        Optional<com.j2ee.HaircutSchedule.entity.Service> service = Optional.empty();
        if (activeOnly) {
            service = serviceRepository.findByIdAndIsActive(id, true);
        } else {
            service = serviceRepository.findById(id);
        }
        return service.map(serviceMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find Service with id: " + id));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    public ServiceResponse createService(
            String name,
            String description,
            BigDecimal price,
            Integer durationMinutes
    ) {
        String validName = ValidationUtils.requireServiceName(name);
        String validDescription = ValidationUtils.optionalText(description, "Description", 500);
        BigDecimal validPrice = ValidationUtils.requireNonNegativePrice(price);
        Integer validDuration = ValidationUtils.requirePositiveDuration(durationMinutes);

        com.j2ee.HaircutSchedule.entity.Service service = com.j2ee.HaircutSchedule.entity.Service.builder()
                .name(validName)
                .description(validDescription)
                .price(validPrice)
                .durationMinutes(validDuration)
                .isActive(true)
                .build();

        return serviceMapper.toResponse(serviceRepository.saveAndFlush(service));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    public ServiceResponse updateService(
            Integer serviceId,
            ArgumentValue<String> name,
            ArgumentValue<String> description,
            ArgumentValue<BigDecimal> price,
            ArgumentValue<Integer> durationMinutes,
            ArgumentValue<Boolean> isActive
    ) {
        com.j2ee.HaircutSchedule.entity.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find Service with id: " + serviceId));

        boolean changed = false;

        if (ValidationUtils.isProvided(name)) {
            service.setName(
                    ValidationUtils.requireServiceName(name.value())
            );
            changed = true;
        }

        if (ValidationUtils.isProvided(description)) {
            service.setDescription(
                    ValidationUtils.optionalText(
                            description.value(),
                            "Description",
                            500
                    )
            );
            changed = true;
        }

        if (ValidationUtils.isProvided(price)) {
            service.setPrice(
                    ValidationUtils.requireNonNegativePrice(price.value())
            );
            changed = true;
        }

        if (ValidationUtils.isProvided(durationMinutes)) {
            service.setDurationMinutes(
                    ValidationUtils.requirePositiveDuration(
                            durationMinutes.value()
                    )
            );
            changed = true;
        }

        if (ValidationUtils.isProvided(isActive)) {
            if (isActive.value() == null) {
                throw new IllegalArgumentException(
                        "isActive cannot be null"
                );
            }

            service.setActive(isActive.value());
            changed = true;
        }

        if (!changed) {
            throw new IllegalArgumentException(
                    "At least one field must be provided"
            );
        }

        return serviceMapper.toResponse(service);
    }
}
