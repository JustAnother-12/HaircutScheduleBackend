package com.j2ee.HaircutSchedule.integration;

import com.j2ee.HaircutSchedule.config.PostgresTestConfiguration;
import com.j2ee.HaircutSchedule.dto.response.BookingResponse;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import com.j2ee.HaircutSchedule.service.BookingService;
import com.j2ee.HaircutSchedule.support.TestDataFactory;
import com.j2ee.HaircutSchedule.support.TestFixtureConfiguration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(authorities = "CUSTOMER")
@Import({
        PostgresTestConfiguration.class,
        TestFixtureConfiguration.class
})
class BookingServiceIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private TestDataFactory fixtures;

    @Test
    void customerCanCreateBooking_success() {
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();

        var service = fixtures.serviceA();

        LocalDate date = fixtures.nextDateForDay(0);

        fixtures.saveSchedule(
                barber,
                0,
                LocalTime.of(8, 0),
                LocalTime.of(18, 0),
                false
        );

        BookingResponse result = bookingService.createBooking(
                customer,
                barber.getId(),
                date,
                LocalTime.of(9, 0),
                List.of(service.getId())
        );

        assertThat(result.id()).isNotNull();
        assertThat(result.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.totalPrice())
                .isEqualByComparingTo("100000");

        assertThat(result.services()).hasSize(1);
    }

    @Test
    void customerCanOnlySeeOwnBookings() {
        User customerA = fixtures.customerA();
        User customerB = fixtures.customerB();
        User barber = fixtures.barberA();

        var service = fixtures.serviceA();

        LocalDate date = fixtures.nextDateForDay(0);

        fixtures.saveBooking(
                customerA,
                barber,
                date,
                LocalTime.of(9, 0),
                LocalTime.of(9, 30),
                BookingStatus.PENDING,
                service
        );

        fixtures.saveBooking(
                customerB,
                barber,
                date,
                LocalTime.of(10, 0),
                LocalTime.of(10, 30),
                BookingStatus.PENDING,
                service
        );

        PageResponse<BookingResponse> result =
                bookingService.getAllBookings(
                        customerA,
                        1,
                        10,
                        null,
                        null,
                        null,
                        null
                );

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).customerId())
                .isEqualTo(customerA.getId());
    }

    @Test
    void customerCannotBookInactiveService() {
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();

        var inactive = fixtures.inactiveService();

        LocalDate date = fixtures.nextDateForDay(0);

        fixtures.saveSchedule(
                barber,
                0,
                LocalTime.of(8, 0),
                LocalTime.of(18, 0),
                false
        );

        assertThatThrownBy(() ->
                bookingService.createBooking(
                        customer,
                        barber.getId(),
                        date,
                        LocalTime.of(9, 0),
                        List.of(inactive.getId())
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Inactive");
    }

    @Test
    void customerCannotBookOverlappingTime() {
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();

        var service = fixtures.serviceA();

        LocalDate date = fixtures.nextDateForDay(0);

        fixtures.saveSchedule(
                barber,
                0,
                LocalTime.of(8, 0),
                LocalTime.of(18, 0),
                false
        );

        fixtures.saveBooking(
                customer,
                barber,
                date,
                LocalTime.of(9, 0),
                LocalTime.of(9, 30),
                BookingStatus.PENDING,
                service
        );

        assertThatThrownBy(() ->
                bookingService.createBooking(
                        customer,
                        barber.getId(),
                        date,
                        LocalTime.of(9, 35),
                        List.of(service.getId())
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlapping");
    }
}
