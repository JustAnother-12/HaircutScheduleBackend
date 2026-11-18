package com.j2ee.HaircutSchedule.repository;

import com.j2ee.HaircutSchedule.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceRepository
        extends JpaRepository<Service, Integer> {

    List<Service> findAllByOrderByNameAsc();

    List<Service> findByIsActiveTrueOrderByNameAsc();
}
