package com.j2ee.HaircutSchedule.integration;

import com.j2ee.HaircutSchedule.config.PostgresTestConfiguration;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.dto.response.BarberScheduleResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.service.BarberScheduleService;
import com.j2ee.HaircutSchedule.support.TestDataFactory;
import com.j2ee.HaircutSchedule.support.TestFixtureConfiguration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import({
        PostgresTestConfiguration.class,
        TestFixtureConfiguration.class
})
class BarberScheduleServiceIntegrationTest {

    @Autowired
    private BarberScheduleService scheduleService;

    @Autowired
    private TestDataFactory fixtures;

    @Test
    void barberCanViewOwnSchedules() {
        User barber = fixtures.barberA();

        fixtures.saveFullWeek(
                barber,
                LocalTime.of(8, 0),
                LocalTime.of(18, 0)
        );

        PageResponse<BarberScheduleResponse> result =
                scheduleService.getAllBarberSchedules(
                        barber,
                        barber.getId(),
                        1,
                        10,
                        null,
                        null
                );

        assertThat(result.items()).hasSize(7);
        assertThat(result.pageInfo().total()).isEqualTo(7);
    }

    @Test
    void adminCanViewAnotherBarberSchedules() {
        User admin = fixtures.admin();
        User barber = fixtures.barberB();

        fixtures.saveSchedule(
                barber,
                0,
                LocalTime.of(13, 0),
                LocalTime.of(21, 0),
                false
        );

        PageResponse<BarberScheduleResponse> result =
                scheduleService.getAllBarberSchedules(
                        admin,
                        barber.getId(),
                        1,
                        10,
                        0,
                        false
                );

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).barberId())
                .isEqualTo(barber.getId());
    }

    @Test
    void barberCannotViewAnotherBarberSchedules() {
        User barberA = fixtures.barberA();
        User barberB = fixtures.barberB();

        assertThatThrownBy(() ->
                scheduleService.getAllBarberSchedules(
                        barberA,
                        barberB.getId(),
                        1,
                        10,
                        null,
                        null
                )
        ).isInstanceOf(AccessDeniedException.class);
    }
}
