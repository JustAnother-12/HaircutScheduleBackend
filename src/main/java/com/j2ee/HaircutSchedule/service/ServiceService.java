package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;
import org.springframework.graphql.data.ArgumentValue;

import java.math.BigDecimal;
import java.util.List;

public interface ServiceService {
    PageResponse<ServiceResponse> getPublicServices(
            Integer page,
            Integer limit,
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minDurationMinutes,
            Integer maxDurationMinutes
    );

    PageResponse<ServiceResponse> getAdminServices(
            Integer page,
            Integer limit,
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minDurationMinutes,
            Integer maxDurationMinutes,
            Boolean isActive
    );
    ServiceResponse getServiceById(Integer id, boolean activeOnly);
    ServiceResponse createService(
            String name,
            String description,
            BigDecimal price,
            Integer durationMinutes
    );
    ServiceResponse updateService(
            Integer serviceId,
            ArgumentValue<String> name,
            ArgumentValue<String> description,
            ArgumentValue<BigDecimal> price,
            ArgumentValue<Integer> durationMinutes,
            ArgumentValue<Boolean> isActive
    );
}
