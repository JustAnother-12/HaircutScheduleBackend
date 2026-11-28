
package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.BarberScheduleResponse;
import com.j2ee.HaircutSchedule.entity.BarberSchedule;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.mapper.BarberScheduleMapper;
import com.j2ee.HaircutSchedule.repository.BarberScheduleRepository;
import com.j2ee.HaircutSchedule.repository.BookingRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import com.j2ee.HaircutSchedule.service.impl.BarberScheduleServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.graphql.data.ArgumentValue;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BarberScheduleServiceImplTest {

    @Mock private BarberScheduleRepository scheduleRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private BarberScheduleMapper scheduleMapper;

    @InjectMocks
    private BarberScheduleServiceImpl scheduleService;

    private User user(Integer id, UserRole role) {
        return User.builder()
                .id(id)
                .fullName("Test " + id)
                .email("test" + id + "@example.com")
                .phone("0901234567")
                .hashedPassword("password-hash")
                .role(role)
                .isActive(true)
                .build();
    }

    @Test
    void updateSchedule_changeTime_success() {
        User barber = user(2, UserRole.BARBER);

        BarberSchedule schedule = BarberSchedule.builder()
                .id(10)
                .barber(barber)
                .dateOfWeek(0)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .isOff(false)
                .build();

        when(scheduleRepository.findByIdAndBarber_Id(10, 2))
                .thenReturn(Optional.of(schedule));

        when(scheduleMapper.toResponse(schedule))
                .thenReturn(
                        BarberScheduleResponse.builder()
                                .id(10)
                                .barberId(2)
                                .dateOfWeek(1)
                                .startTime(LocalTime.of(9, 0))
                                .endTime(LocalTime.of(13, 0))
                                .isOff(false)
                                .build()
                );

        scheduleService.updateBarberSchedule(
                barber,
                2,
                10,
                ArgumentValue.ofNullable(1),
                ArgumentValue.ofNullable(LocalTime.of(9, 0)),
                ArgumentValue.ofNullable(LocalTime.of(13, 0)),
                ArgumentValue.omitted()
        );

        assertThat(schedule.getDateOfWeek()).isEqualTo(1);
        assertThat(schedule.getStartTime())
                .isEqualTo(LocalTime.of(9, 0));
        assertThat(schedule.getEndTime())
                .isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void createSchedule_validInput_success() {
        User barber = user(2, UserRole.BARBER);

        when(userRepository.findByIdAndRole(2, UserRole.BARBER))
                .thenReturn(Optional.of(barber));

        when(scheduleRepository.saveAndFlush(
                any(BarberSchedule.class)
        )).thenAnswer(invocation -> {
            BarberSchedule saved = invocation.getArgument(0);
            saved.setId(10);
            return saved;
        });

        when(scheduleRepository
                .findByBarber_IdAndDateOfWeekOrderByStartTimeAsc(2, 0))
                .thenReturn(java.util.List.of());

        when(scheduleMapper.toResponse(any(BarberSchedule.class)))
                .thenAnswer(invocation -> {
                    BarberSchedule saved = invocation.getArgument(0);

                    return BarberScheduleResponse.builder()
                            .id(saved.getId())
                            .barberId(2)
                            .dateOfWeek(saved.getDateOfWeek())
                            .startTime(saved.getStartTime())
                            .endTime(saved.getEndTime())
                            .isOff(saved.isOff())
                            .build();
                });

        BarberScheduleResponse result =
                scheduleService.createBarberSchedule(
                        barber,
                        2,
                        0,
                        LocalTime.of(8, 0),
                        LocalTime.of(18, 0),
                        false
                );

        assertThat(result.id()).isEqualTo(10);
        assertThat(result.dateOfWeek()).isEqualTo(0);
    }

    @Test
    void createSchedule_customerCannotCreateSchedule() {
        User customer = user(1, UserRole.CUSTOMER);

        assertThatThrownBy(() ->
                scheduleService.createBarberSchedule(
                        customer,
                        2,
                        0,
                        LocalTime.of(8, 0),
                        LocalTime.of(18, 0),
                        false
                )
        ).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(scheduleRepository, userRepository);
    }

    @Test
    void createSchedule_invalidTimeRange_throwsException() {
        User admin = user(3, UserRole.ADMIN);
        User barber = user(2, UserRole.BARBER);

        when(userRepository.findByIdAndRole(2, UserRole.BARBER))
                .thenReturn(Optional.of(barber));

        assertThatThrownBy(() ->
                scheduleService.createBarberSchedule(
                        admin,
                        2,
                        0,
                        LocalTime.of(18, 0),
                        LocalTime.of(8, 0),
                        false
                )
        ).isInstanceOf(IllegalArgumentException.class);

        verify(scheduleRepository, never()).saveAndFlush(any(BarberSchedule.class));
    }
}

