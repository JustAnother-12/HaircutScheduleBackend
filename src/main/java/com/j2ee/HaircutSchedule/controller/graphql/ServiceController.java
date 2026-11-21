package com.j2ee.HaircutSchedule.controller.graphql;

import com.j2ee.HaircutSchedule.dto.request.CreateServiceInput;
import com.j2ee.HaircutSchedule.dto.request.UpdateServiceInput;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;
import com.j2ee.HaircutSchedule.service.ServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static com.j2ee.HaircutSchedule.util.Helpers.parseId;


@Controller
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceController {
    private final ServiceService serviceService;

    @QueryMapping
    public PageResponse<ServiceResponse> services(
            @Argument Integer page,
            @Argument Integer limit,
            @Argument String name,
            @Argument BigDecimal minPrice,
            @Argument BigDecimal maxPrice,
            @Argument Integer minDurationMinutes,
            @Argument Integer maxDurationMinutes
    ) {
        return serviceService.getPublicServices(
                page,
                limit,
                name,
                minPrice,
                maxPrice,
                minDurationMinutes,
                maxDurationMinutes
        );
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public PageResponse<ServiceResponse> adminServices(
            @Argument Integer page,
            @Argument Integer limit,
            @Argument String name,
            @Argument BigDecimal minPrice,
            @Argument BigDecimal maxPrice,
            @Argument Integer minDurationMinutes,
            @Argument Integer maxDurationMinutes,
            @Argument Boolean isActive
    ) {
        return serviceService.getAdminServices(
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

    @QueryMapping
    public ServiceResponse service(@Argument String id) {
        return serviceService.getServiceById(
                parseId(id),
                true
        );
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ServiceResponse adminService(@Argument String id) {
        return serviceService.getServiceById(
                parseId(id),
                false
        );
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ServiceResponse createService(
            @Argument CreateServiceInput input
            ) {
        return serviceService.createService(
                input.name(),
                input.description(),
                input.price(),
                input.durationMinutes()
        );
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ServiceResponse updateService(
            @Argument String id,
            @Argument UpdateServiceInput input
    ) {
        return serviceService.updateService(
                parseId(id),
                input.name(),
                input.description(),
                input.price(),
                input.durationMinutes(),
                input.isActive()
        );
    }
}
