package com.j2ee.HaircutSchedule.mapper;

import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;
import com.j2ee.HaircutSchedule.entity.Service;
import org.springframework.stereotype.Component;

@Component
public class ServiceMapper {
    public ServiceResponse toResponse(Service service) {
        if (service == null) {
            return null;
        }

        return ServiceResponse.builder()
                .id(service.getId())
                .name(service.getName())
                .description(service.getDescription())
                .price(service.getPrice())
                .durationMinutes(service.getDurationMinutes())
                .isActive(service.isActive())
                .build();
    }
}
