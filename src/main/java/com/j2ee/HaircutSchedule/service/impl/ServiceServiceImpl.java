package com.j2ee.HaircutSchedule.service.impl;

import com.j2ee.HaircutSchedule.dto.response.ServiceResponse;
import com.j2ee.HaircutSchedule.mapper.ServiceMapper;
import com.j2ee.HaircutSchedule.repository.ServiceRepository;

import java.util.List;

import com.j2ee.HaircutSchedule.service.ServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


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
    public ServiceResponse getServiceById(Integer id) {

        return serviceRepository.findById(id)
                .map(serviceMapper::toResponse)
                .orElse(null);
    }
}
