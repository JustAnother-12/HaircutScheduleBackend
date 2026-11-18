package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;

import java.util.List;

public interface ServiceService {
    List<ServiceResponse> getAllServices(boolean activeOnly);

    ServiceResponse getServiceById(Integer id);
}
