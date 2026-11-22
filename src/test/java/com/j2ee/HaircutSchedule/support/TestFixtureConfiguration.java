package com.j2ee.HaircutSchedule.support;

import com.j2ee.HaircutSchedule.repository.BarberScheduleRepository;
import com.j2ee.HaircutSchedule.repository.BookingRepository;
import com.j2ee.HaircutSchedule.repository.ServiceRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@TestConfiguration(proxyBeanMethods = false)
public class TestFixtureConfiguration {

    @Bean
    TestDataFactory testDataFactory(
            UserRepository userRepository,
            ServiceRepository serviceRepository,
            BarberScheduleRepository scheduleRepository,
            BookingRepository bookingRepository,
            PasswordEncoder passwordEncoder
    ) {
        return new TestDataFactory(
                userRepository,
                serviceRepository,
                scheduleRepository,
                bookingRepository,
                passwordEncoder
        );
    }
}
