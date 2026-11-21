package com.j2ee.HaircutSchedule.repository;

import com.j2ee.HaircutSchedule.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends
        JpaRepository<Service, Integer>,
        JpaSpecificationExecutor<Service> {

    Optional<Service> findByIdAndIsActive(Integer id, boolean isActive);
    List<Service> findAllByOrderByNameAsc();
    List<Service> findByIsActiveTrueOrderByNameAsc();
}
