package com.j2ee.HaircutSchedule.service.impl;

import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.mapper.ServiceMapper;
import com.j2ee.HaircutSchedule.repository.ServiceRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.j2ee.HaircutSchedule.service.ServiceService;
import com.j2ee.HaircutSchedule.util.PageUtils;
import com.j2ee.HaircutSchedule.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceServiceImpl implements ServiceService {
    private final ServiceRepository serviceRepository;
    private final ServiceMapper serviceMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ServiceResponse> getPublicServices(
            Integer page,
            Integer limit,
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minDurationMinutes,
            Integer maxDurationMinutes
    ) {
        return searchServices(
                page,
                limit,
                name,
                minPrice,
                maxPrice,
                minDurationMinutes,
                maxDurationMinutes,
                true
        );
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ADMIN')")
    public PageResponse<ServiceResponse> getAdminServices(
            Integer page,
            Integer limit,
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minDurationMinutes,
            Integer maxDurationMinutes,
            Boolean isActive
    ) {
        return searchServices(
                page,
                limit,
                name,
                minPrice,
                maxPrice,
                minDurationMinutes,
                maxDurationMinutes,
                isActive
        );
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

    private PageResponse<ServiceResponse> searchServices(
            Integer page,
            Integer limit,
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minDurationMinutes,
            Integer maxDurationMinutes,
            Boolean isActive
    ) {
        validateServiceFilters(
                minPrice,
                maxPrice,
                minDurationMinutes,
                maxDurationMinutes
        );

        Specification<com.j2ee.HaircutSchedule.entity.Service> spec =
                (root, query, cb) -> cb.conjunction();

        if (isActive != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("isActive"), isActive)
            );
        }

        String namePattern = PageUtils.containsIgnoreCase(name);

        if (namePattern != null) {
            spec = spec.and((root, query, cb) ->
                    cb.like(
                            cb.lower(root.get("name")),
                            namePattern
                    )
            );
        }

        if (minPrice != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(
                            root.<BigDecimal>get("price"),
                            minPrice
                    )
            );
        }

        if (maxPrice != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(
                            root.<BigDecimal>get("price"),
                            maxPrice
                    )
            );
        }

        if (minDurationMinutes != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(
                            root.<Integer>get("durationMinutes"),
                            minDurationMinutes
                    )
            );
        }

        if (maxDurationMinutes != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(
                            root.<Integer>get("durationMinutes"),
                            maxDurationMinutes
                    )
            );
        }

        Page<com.j2ee.HaircutSchedule.entity.Service> result =
                serviceRepository.findAll(
                        spec,
                        PageUtils.pageable(
                                page,
                                limit,
                                Sort.by(
                                        Sort.Order.asc("name"),
                                        Sort.Order.asc("id")
                                )
                        )
                );

        return PageResponse.from(result, serviceMapper::toResponse);
    }

    private void validateServiceFilters(
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minDuration,
            Integer maxDuration
    ) {
        if (minPrice != null && minPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "minPrice cannot be negative"
            );
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "maxPrice cannot be negative"
            );
        }
        if (minPrice != null && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(
                    "minPrice cannot be greater than maxPrice"
            );
        }
        if (minDuration != null && minDuration <= 0) {
            throw new IllegalArgumentException(
                    "minDurationMinutes must be greater than zero"
            );
        }
        if (maxDuration != null && maxDuration <= 0) {
            throw new IllegalArgumentException(
                    "maxDurationMinutes must be greater than zero"
            );
        }
        if (minDuration != null && maxDuration != null
                && minDuration > maxDuration) {
            throw new IllegalArgumentException(
                    "minDurationMinutes cannot exceed maxDurationMinutes"
            );
        }
    }
}
