package com.j2ee.HaircutSchedule.controller.graphql;

import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;
import com.j2ee.HaircutSchedule.service.ServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Controller
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceController {
    private final ServiceService serviceService;

    @QueryMapping
    public List<ServiceResponse> services(
            @Argument Boolean activeOnly
    ) {
        return serviceService.getAllServices(activeOnly);
    }
}
