package com.j2ee.HaircutSchedule.integration;

import com.j2ee.HaircutSchedule.config.PostgresTestConfiguration;
import com.j2ee.HaircutSchedule.dto.response.AvailableSlotResponse;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.dto.response.BarberScheduleResponse;
import com.j2ee.HaircutSchedule.entity.BarberSchedule;
import com.j2ee.HaircutSchedule.entity.Service;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.repository.BarberScheduleRepository;
import com.j2ee.HaircutSchedule.repository.BookingRepository;
import com.j2ee.HaircutSchedule.service.BarberScheduleService;
import com.j2ee.HaircutSchedule.support.TestDataFactory;
import com.j2ee.HaircutSchedule.support.TestFixtureConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private BarberScheduleRepository scheduleRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TestDataFactory fixtures;

    @PersistenceContext
    private EntityManager entityManager;

    private AvailableSlotResponse availableSlots(
            User barber,
            LocalDate date,
            int duration
    ) {
        return scheduleService.getAvailableSlots(barber.getId(), date, duration);
    }

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

    @Test
    void createSchedule_persistsCorrectly() {
        // Arrange
        User barber = fixtures.barberA();
        int dayOfWeek = 2;
        LocalTime start = LocalTime.of(8, 30);
        LocalTime end = LocalTime.of(17, 30);

        // Execute
        BarberScheduleResponse result = scheduleService.createBarberSchedule(
                barber, barber.getId(), dayOfWeek, start, end, false
        );

        // Assert: flush and reload from PostgreSQL.
        entityManager.flush();
        entityManager.clear();

        BarberSchedule persisted = scheduleRepository.findById(result.id()).orElseThrow();
        assertThat(result.id()).isNotNull();
        assertThat(persisted.getBarber().getId()).isEqualTo(barber.getId());
        assertThat(persisted.getDateOfWeek()).isEqualTo(dayOfWeek);
        assertThat(persisted.getStartTime()).isEqualTo(start);
        assertThat(persisted.getEndTime()).isEqualTo(end);
        assertThat(persisted.isOff()).isFalse();
    }

    @Test
    void createSchedule_rejectsCustomerAsTargetBarber() {
        // Arrange
        User admin = fixtures.admin();
        User customer = fixtures.customerA();
        long countBefore = scheduleRepository.count();

        // Execute & Assert
        assertThatThrownBy(() -> scheduleService.createBarberSchedule(
                admin,
                customer.getId(),
                0,
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                false
        )).isInstanceOf(ResourceNotFoundException.class);

        assertThat(scheduleRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void updateSchedule_persistsPartialChanges() {
        // Arrange
        User barber = fixtures.barberA();
        BarberSchedule schedule = fixtures.saveSchedule(
                barber, 1, LocalTime.of(8, 0), LocalTime.of(18, 0), false
        );

        // Execute: only update startTime; omitted fields must be retained.
        BarberScheduleResponse result = scheduleService.updateBarberSchedule(
                barber,
                barber.getId(),
                schedule.getId(),
                ArgumentValue.omitted(),
                ArgumentValue.ofNullable(LocalTime.of(9, 0)),
                ArgumentValue.omitted(),
                ArgumentValue.omitted()
        );

        // Assert after reloading from PostgreSQL.
        entityManager.flush();
        entityManager.clear();

        BarberSchedule persisted = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(result.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(persisted.getDateOfWeek()).isEqualTo(1);
        assertThat(persisted.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(persisted.getEndTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(persisted.isOff()).isFalse();
    }

    @Test
    void updateSchedule_rejectsInvalidTimeRange() {
        // Arrange
        User barber = fixtures.barberA();
        BarberSchedule schedule = fixtures.saveSchedule(
                barber, 0, LocalTime.of(8, 0), LocalTime.of(12, 0), false
        );

        // Execute & Assert
        assertThatThrownBy(() -> scheduleService.updateBarberSchedule(
                barber,
                barber.getId(),
                schedule.getId(),
                ArgumentValue.omitted(),
                ArgumentValue.ofNullable(LocalTime.of(13, 0)),
                ArgumentValue.omitted(),
                ArgumentValue.omitted()
        )).isInstanceOf(IllegalArgumentException.class);

        // The failed transaction must not persist the invalid start time.
        entityManager.clear();
        BarberSchedule unchanged = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(unchanged.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(unchanged.getEndTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void updateSchedule_rejectsScheduleOwnedByAnotherBarber() {
        // Arrange
        User barberA = fixtures.barberA();
        User barberB = fixtures.barberB();
        BarberSchedule scheduleB = fixtures.saveSchedule(
                barberB, 0, LocalTime.of(8, 0), LocalTime.of(18, 0), false
        );

        // Execute & Assert: barber A addresses schedule B through barber A's scope.
        assertThatThrownBy(() -> scheduleService.updateBarberSchedule(
                barberA,
                barberA.getId(),
                scheduleB.getId(),
                ArgumentValue.ofNullable(1),
                ArgumentValue.omitted(),
                ArgumentValue.omitted(),
                ArgumentValue.omitted()
        )).isInstanceOf(ResourceNotFoundException.class);

        entityManager.clear();
        BarberSchedule unchanged = scheduleRepository.findById(scheduleB.getId()).orElseThrow();
        assertThat(unchanged.getDateOfWeek()).isEqualTo(0);
        assertThat(unchanged.getStartTime()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void deleteSchedule_removesRecord() {
        // Arrange
        User barber = fixtures.barberA();
        BarberSchedule schedule = fixtures.saveSchedule(
                barber, 0, LocalTime.of(8, 0), LocalTime.of(18, 0), false
        );

        // Execute
        boolean deleted = scheduleService.deleteBarberSchedule(
                barber, barber.getId(), schedule.getId()
        );

        // Assert
        entityManager.flush();
        entityManager.clear();
        assertThat(deleted).isTrue();
        assertThat(scheduleRepository.findById(schedule.getId())).isEmpty();
    }

    @Test
    void scheduleFilterByDateOfWeek() {
        // Arrange
        User barber = fixtures.barberA();
        fixtures.saveFullWeek(barber, LocalTime.of(8, 0), LocalTime.of(18, 0));

        // Execute
        PageResponse<BarberScheduleResponse> result = scheduleService.getAllBarberSchedules(
                barber, barber.getId(), 1, 10, 3, null
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.items()).allMatch(item -> item.dateOfWeek() == 3);
        assertThat(result.pageInfo().total()).isEqualTo(1);
    }

    @Test
    void availableSlots_noBookings_returnsAllSlots() {
        // Arrange
        User barber = fixtures.barberA();
        LocalDate date = fixtures.nextDateForDay(0);
        fixtures.saveSchedule(
                barber, 0, LocalTime.of(8, 0), LocalTime.of(10, 0), false
        );

        // Execute
        AvailableSlotResponse result = availableSlots(barber, date, 30);

        // Assert
        assertThat(result.bookingDate()).isEqualTo(date);
        assertThat(result.slotDurationMinutes()).isEqualTo(30);
        assertThat(result.availableSlots()).containsExactly(
                new AvailableSlotResponse.TimeSlot(LocalTime.of(8, 0), LocalTime.of(8, 30)),
                new AvailableSlotResponse.TimeSlot(LocalTime.of(8, 30), LocalTime.of(9, 0)),
                new AvailableSlotResponse.TimeSlot(LocalTime.of(9, 0), LocalTime.of(9, 30)),
                new AvailableSlotResponse.TimeSlot(LocalTime.of(9, 30), LocalTime.of(10, 0))
        );
    }

    @Test
    void availableSlots_pendingBooking_excludesBusySlots() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        LocalDate date = fixtures.nextDateForDay(0);
        fixtures.saveSchedule(barber, 0, LocalTime.of(8, 0), LocalTime.of(12, 0), false);
        fixtures.saveBooking(
                customer, barber, date, LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.PENDING, service
        );

        // Execute
        AvailableSlotResponse result = availableSlots(barber, date, 30);

        // Assert: booking 09:00-09:30 and its 10-minute buffer block both 09:00 and 09:30 slots.
        assertThat(result.availableSlots())
                .doesNotContain(
                        new AvailableSlotResponse.TimeSlot(LocalTime.of(9, 0), LocalTime.of(9, 30)),
                        new AvailableSlotResponse.TimeSlot(LocalTime.of(9, 30), LocalTime.of(10, 0))
                );
        assertThat(result.availableSlots()).contains(
                new AvailableSlotResponse.TimeSlot(LocalTime.of(8, 30), LocalTime.of(9, 0)),
                new AvailableSlotResponse.TimeSlot(LocalTime.of(10, 0), LocalTime.of(10, 30))
        );
    }

    @Test
    void availableSlots_confirmedBooking_excludesBusySlots() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        LocalDate date = fixtures.nextDateForDay(0);
        fixtures.saveSchedule(barber, 0, LocalTime.of(8, 0), LocalTime.of(12, 0), false);
        fixtures.saveBooking(
                customer, barber, date, LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.CONFIRMED, service
        );

        // Execute
        AvailableSlotResponse result = availableSlots(barber, date, 30);

        // Assert
        assertThat(result.availableSlots()).doesNotContain(
                new AvailableSlotResponse.TimeSlot(LocalTime.of(9, 0), LocalTime.of(9, 30)),
                new AvailableSlotResponse.TimeSlot(LocalTime.of(9, 30), LocalTime.of(10, 0))
        );
    }

    @Test
    void availableSlots_noWorkingSchedule_returnsEmpty() {
        // Arrange
        User barber = fixtures.barberA();
        LocalDate date = fixtures.nextDateForDay(0);
        // Intentionally do not create a schedule for this weekday.

        // Execute
        AvailableSlotResponse result = availableSlots(barber, date, 30);

        // Assert
        assertThat(result.bookingDate()).isEqualTo(date);
        assertThat(result.slotDurationMinutes()).isEqualTo(30);
        assertThat(result.availableSlots()).isEmpty();
    }

    @Test
    void schedulePagination_returnsStableOrder() {
        // Arrange
        User barber = fixtures.barberA();
        fixtures.saveSchedule(barber, 1, LocalTime.of(10, 0), LocalTime.of(18, 0), false);
        fixtures.saveSchedule(barber, 0, LocalTime.of(12, 0), LocalTime.of(18, 0), false);
        fixtures.saveSchedule(barber, 0, LocalTime.of(8, 0), LocalTime.of(12, 0), false);
        fixtures.saveSchedule(barber, 2, LocalTime.of(9, 0), LocalTime.of(18, 0), false);
        fixtures.saveSchedule(barber, 1, LocalTime.of(8, 0), LocalTime.of(10, 0), false);

        // Execute
        PageResponse<BarberScheduleResponse> first = scheduleService.getAllBarberSchedules(
                barber, barber.getId(), 1, 2, null, null
        );
        PageResponse<BarberScheduleResponse> second = scheduleService.getAllBarberSchedules(
                barber, barber.getId(), 2, 2, null, null
        );
        PageResponse<BarberScheduleResponse> third = scheduleService.getAllBarberSchedules(
                barber, barber.getId(), 3, 2, null, null
        );

        // Assert
        List<BarberScheduleResponse> all = new ArrayList<>();
        all.addAll(first.items());
        all.addAll(second.items());
        all.addAll(third.items());

        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(2);
        assertThat(third.items()).hasSize(1);
        assertThat(first.pageInfo().total()).isEqualTo(5);
        assertThat(first.pageInfo().pages()).isEqualTo(3);

        for (int i = 1; i < all.size(); i++) {
            BarberScheduleResponse previous = all.get(i - 1);
            BarberScheduleResponse current = all.get(i);
            assertThat(current.dateOfWeek()).isGreaterThanOrEqualTo(previous.dateOfWeek());
            if (current.dateOfWeek().equals(previous.dateOfWeek())) {
                assertThat(current.startTime()).isAfterOrEqualTo(previous.startTime());
            }
        }

        Set<Integer> ids = new HashSet<>(all.stream().map(BarberScheduleResponse::id).toList());
        assertThat(ids).hasSize(5);
    }

    @Test
    void availableSlots_completedBooking_doesNotBlock() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        LocalDate date = fixtures.nextDateForDay(0);
        fixtures.saveSchedule(barber, 0, LocalTime.of(8, 0), LocalTime.of(10, 0), false);
        fixtures.saveBooking(
                customer, barber, date, LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.COMPLETED, service
        );

        // Execute
        AvailableSlotResponse result = availableSlots(barber, date, 30);

        // Assert
        assertThat(result.availableSlots()).contains(
                new AvailableSlotResponse.TimeSlot(LocalTime.of(9, 0), LocalTime.of(9, 30)),
                new AvailableSlotResponse.TimeSlot(LocalTime.of(9, 30), LocalTime.of(10, 0))
        );
    }

    @Test
    void availableSlots_cancelledBooking_doesNotBlock() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        LocalDate date = fixtures.nextDateForDay(0);
        fixtures.saveSchedule(barber, 0, LocalTime.of(8, 0), LocalTime.of(10, 0), false);
        fixtures.saveBooking(
                customer, barber, date, LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.CANCELLED, service
        );

        // Execute
        AvailableSlotResponse result = availableSlots(barber, date, 30);

        // Assert
        assertThat(result.availableSlots()).contains(
                new AvailableSlotResponse.TimeSlot(LocalTime.of(9, 0), LocalTime.of(9, 30)),
                new AvailableSlotResponse.TimeSlot(LocalTime.of(9, 30), LocalTime.of(10, 0))
        );
    }

    @Test
    void availableSlots_slotDoesNotCrossScheduleEnd() {
        // Arrange
        User barber = fixtures.barberA();
        LocalDate date = fixtures.nextDateForDay(0);
        fixtures.saveSchedule(
                barber, 0, LocalTime.of(8, 0), LocalTime.of(9, 50), false
        );

        // Execute
        AvailableSlotResponse result = availableSlots(barber, date, 30);

        // Assert
        assertThat(result.availableSlots()).containsExactly(
                new AvailableSlotResponse.TimeSlot(LocalTime.of(8, 0), LocalTime.of(8, 30)),
                new AvailableSlotResponse.TimeSlot(LocalTime.of(8, 30), LocalTime.of(9, 0)),
                new AvailableSlotResponse.TimeSlot(LocalTime.of(9, 0), LocalTime.of(9, 30))
        );
        assertThat(result.availableSlots())
                .allMatch(slot -> !slot.endTime().isAfter(LocalTime.of(9, 50)));
    }
}
