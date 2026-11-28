package com.j2ee.HaircutSchedule.integration;

import com.j2ee.HaircutSchedule.config.PostgresTestConfiguration;
import com.j2ee.HaircutSchedule.dto.response.BookingResponse;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.entity.Booking;
import com.j2ee.HaircutSchedule.entity.Service;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.repository.BookingRepository;
import com.j2ee.HaircutSchedule.repository.ServiceRepository;
import com.j2ee.HaircutSchedule.service.BookingService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private BookingRepository bookingRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private TestDataFactory fixtures;

    @PersistenceContext
    private EntityManager entityManager;

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

    @Test
    void createBooking_persistsBookingAndBookingServices() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service serviceA = fixtures.serviceA();
        Service serviceB = fixtures.saveService(
                "Hair treatment", "Second service", new BigDecimal("120000"), 45, true
        );
        LocalDate date = fixtures.nextDateForDay(0);

        fixtures.saveSchedule(
                barber, 0, LocalTime.of(8, 0), LocalTime.of(18, 0), false
        );

        // Execute
        BookingResponse result = bookingService.createBooking(
                customer,
                barber.getId(),
                date,
                LocalTime.of(9, 0),
                List.of(serviceA.getId(), serviceB.getId())
        );

        // Assert response
        assertThat(result.id()).isNotNull();
        assertThat(result.customerId()).isEqualTo(customer.getId());
        assertThat(result.barberId()).isEqualTo(barber.getId());
        assertThat(result.bookingDate()).isEqualTo(date);
        assertThat(result.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.endTime()).isEqualTo(LocalTime.of(10, 15));
        assertThat(result.totalPrice()).isEqualByComparingTo("220000");
        assertThat(result.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.services()).hasSize(2);

        // Assert persistence after clearing the first-level cache.
        entityManager.flush();
        entityManager.clear();

        Booking persisted = bookingRepository.findById(result.id()).orElseThrow();
        assertThat(persisted.getBookingServices()).hasSize(2);
        assertThat(persisted.getBookingServices())
                .extracting(bs -> bs.getPriceAtBooking())
                .usingComparatorForType(
                        BigDecimal::compareTo,
                        BigDecimal.class
                )
                .containsExactlyInAnyOrder(
                        new BigDecimal("100000"),
                        new BigDecimal("120000")
                );
        assertThat(persisted.getTotalPrice()).isEqualByComparingTo("220000");
        assertThat(persisted.getEndTime()).isEqualTo(LocalTime.of(10, 15));
    }

    @Test
    void createBooking_rejectsInactiveBarber() {
        // Arrange
        User customer = fixtures.customerA();
        User inactiveBarber = fixtures.saveUser(
                "Inactive Barber", "inactive.booking.barber@example.com", UserRole.BARBER, false
        );
        Service service = fixtures.serviceA();
        long countBefore = bookingRepository.count();

        // Execute & Assert
        assertThatThrownBy(() -> bookingService.createBooking(
                customer,
                inactiveBarber.getId(),
                fixtures.nextDateForDay(0),
                LocalTime.of(9, 0),
                List.of(service.getId())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The requested Barber is inactive");

        assertThat(bookingRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void createBooking_rejectsPastDate() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        long countBefore = bookingRepository.count();

        // Execute & Assert
        assertThatThrownBy(() -> bookingService.createBooking(
                customer,
                barber.getId(),
                yesterday,
                LocalTime.of(9, 0),
                List.of(service.getId())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Booking date must not be from the past");

        assertThat(bookingRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void createBooking_rejectsUnknownServiceId() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service knownService = fixtures.serviceA();
        LocalDate date = fixtures.nextDateForDay(0);
        fixtures.saveSchedule(barber, 0, LocalTime.of(8, 0), LocalTime.of(18, 0), false);
        long countBefore = bookingRepository.count();

        // Execute & Assert
        assertThatThrownBy(() -> bookingService.createBooking(
                customer,
                barber.getId(),
                date,
                LocalTime.of(9, 0),
                List.of(knownService.getId(), Integer.MAX_VALUE)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("One of the services does not exist");

        assertThat(bookingRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void getBookings_barberOnlySeesAssignedBookings() {
        // Arrange
        User customer = fixtures.customerA();
        User barberA = fixtures.barberA();
        User barberB = fixtures.barberB();
        Service service = fixtures.serviceA();
        LocalDate date = fixtures.nextDateForDay(0);

        Booking assignedToA = fixtures.saveBooking(
                customer, barberA, date, LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.PENDING, service
        );
        fixtures.saveBooking(
                customer, barberB, date, LocalTime.of(10, 0), LocalTime.of(10, 30),
                BookingStatus.PENDING, service
        );

        // Execute
        PageResponse<BookingResponse> result = bookingService.getAllBookings(
                barberA, 1, 10, null, null, null, null
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(assignedToA.getId());
        assertThat(result.items()).allMatch(item -> item.barberId().equals(barberA.getId()));
    }

    @Test
    void getBookings_adminSeesAllBookings() {
        // Arrange
        User admin = fixtures.admin();
        User customerA = fixtures.customerA();
        User customerB = fixtures.customerB();
        User barberA = fixtures.barberA();
        User barberB = fixtures.barberB();
        Service service = fixtures.serviceA();
        LocalDate date = fixtures.nextDateForDay(0);

        fixtures.saveBooking(
                customerA, barberA, date, LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.PENDING, service
        );
        fixtures.saveBooking(
                customerB, barberB, date, LocalTime.of(10, 0), LocalTime.of(10, 30),
                BookingStatus.CONFIRMED, service
        );

        // Execute
        PageResponse<BookingResponse> result = bookingService.getAllBookings(
                admin, 1, 10, null, null, null, null
        );

        // Assert
        assertThat(result.items()).hasSize(2);
        assertThat(result.pageInfo().total()).isEqualTo(2);
        assertThat(result.items())
                .extracting(BookingResponse::customerId)
                .containsExactlyInAnyOrder(customerA.getId(), customerB.getId());
    }

    @Test
    void getBookings_filterByDateAndStatus() {
        // Arrange
        User admin = fixtures.admin();
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        LocalDate targetDate = fixtures.nextDateForDay(0);
        LocalDate anotherDate = fixtures.nextDateForDay(1);

        Booking expected = fixtures.saveBooking(
                customer, barber, targetDate, LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.CONFIRMED, service
        );
        fixtures.saveBooking(
                customer, barber, targetDate, LocalTime.of(10, 0), LocalTime.of(10, 30),
                BookingStatus.PENDING, service
        );
        fixtures.saveBooking(
                customer, barber, anotherDate, LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.CONFIRMED, service
        );

        // Execute
        PageResponse<BookingResponse> result = bookingService.getAllBookings(
                admin, 1, 10, targetDate, BookingStatus.CONFIRMED, null, null
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(expected.getId());
        assertThat(result.items().get(0).bookingDate()).isEqualTo(targetDate);
        assertThat(result.items().get(0).status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void getBookingById_ownerCanRead() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        LocalDate date = fixtures.nextDateForDay(0);
        Booking booking = fixtures.saveBooking(
                customer, barber, date, LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.PENDING, service
        );

        // Execute
        BookingResponse result = bookingService.getBookingById(customer, booking.getId());

        // Assert
        assertThat(result.id()).isEqualTo(booking.getId());
        assertThat(result.customerId()).isEqualTo(customer.getId());
        assertThat(result.barberId()).isEqualTo(barber.getId());
        assertThat(result.status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void getBookingById_otherCustomerDenied() {
        // Arrange
        User owner = fixtures.customerA();
        User otherCustomer = fixtures.customerB();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        Booking booking = fixtures.saveBooking(
                owner, barber, fixtures.nextDateForDay(0),
                LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.PENDING, service
        );

        // Execute & Assert
        assertThatThrownBy(() -> bookingService.getBookingById(
                otherCustomer, booking.getId()
        )).isInstanceOf(AccessDeniedException.class)
                .hasMessage("You cannot access this booking");
    }

    @Test
    void cancelBooking_ownerCanCancel() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        Booking booking = fixtures.saveBooking(
                customer, barber, fixtures.nextDateForDay(0),
                LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.PENDING, service
        );

        // Execute
        BookingResponse result = bookingService.cancelBooking(customer, booking.getId());

        // Assert after reloading
        entityManager.flush();
        entityManager.clear();
        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(persisted.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBooking_otherCustomerDenied() {
        // Arrange
        User owner = fixtures.customerA();
        User otherCustomer = fixtures.customerB();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        Booking booking = fixtures.saveBooking(
                owner, barber, fixtures.nextDateForDay(0),
                LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.PENDING, service
        );

        // Execute & Assert
        assertThatThrownBy(() -> bookingService.cancelBooking(
                otherCustomer, booking.getId()
        )).isInstanceOf(AccessDeniedException.class);

        entityManager.clear();
        Booking unchanged = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void rescheduleBooking_ownerCanReschedule() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        LocalDate date = fixtures.nextDateForDay(0);
        fixtures.saveSchedule(barber, 0, LocalTime.of(8, 0), LocalTime.of(18, 0), false);
        Booking booking = fixtures.saveBooking(
                customer, barber, date, LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.PENDING, service
        );

        // Execute
        BookingResponse result = bookingService.updateBookingSchedule(
                customer,
                booking.getId(),
                ArgumentValue.omitted(),
                ArgumentValue.ofNullable(LocalTime.of(10, 0))
        );

        // Assert after reload
        entityManager.flush();
        entityManager.clear();
        Booking persisted = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(result.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(result.endTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(persisted.getBookingDate()).isEqualTo(date);
        assertThat(persisted.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(persisted.getEndTime()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void rescheduleBooking_rejectsOverlap() {
        // Arrange
        User customerA = fixtures.customerA();
        User customerB = fixtures.customerB();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        LocalDate date = fixtures.nextDateForDay(0);
        fixtures.saveSchedule(barber, 0, LocalTime.of(8, 0), LocalTime.of(18, 0), false);

        Booking toReschedule = fixtures.saveBooking(
                customerA, barber, date, LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.PENDING, service
        );
        fixtures.saveBooking(
                customerB, barber, date, LocalTime.of(11, 0), LocalTime.of(11, 30),
                BookingStatus.CONFIRMED, service
        );

        // Execute & Assert: requested 11:00-11:30 overlaps the existing booking and buffer.
        assertThatThrownBy(() -> bookingService.updateBookingSchedule(
                customerA,
                toReschedule.getId(),
                ArgumentValue.omitted(),
                ArgumentValue.ofNullable(LocalTime.of(11, 0))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlapping");

        entityManager.clear();
        Booking unchanged = bookingRepository.findById(toReschedule.getId()).orElseThrow();
        assertThat(unchanged.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(unchanged.getEndTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void rescheduleBooking_excludesCurrentBooking() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        LocalDate date = fixtures.nextDateForDay(0);
        fixtures.saveSchedule(barber, 0, LocalTime.of(8, 0), LocalTime.of(18, 0), false);
        Booking booking = fixtures.saveBooking(
                customer, barber, date, LocalTime.of(9, 0), LocalTime.of(9, 30),
                BookingStatus.PENDING, service
        );

        // Execute: retain the same date and start time. The booking itself must be excluded.
        BookingResponse result = bookingService.updateBookingSchedule(
                customer,
                booking.getId(),
                ArgumentValue.ofNullable(date),
                ArgumentValue.ofNullable(LocalTime.of(9, 0))
        );

        // Assert
        assertThat(result.id()).isEqualTo(booking.getId());
        assertThat(result.bookingDate()).isEqualTo(date);
        assertThat(result.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.endTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void createBooking_rejectsDuplicateServiceIds() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        long countBefore = bookingRepository.count();

        // Execute & Assert
        assertThatThrownBy(() -> bookingService.createBooking(
                customer,
                barber.getId(),
                fixtures.nextDateForDay(0),
                LocalTime.of(9, 0),
                List.of(service.getId(), service.getId())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate service IDs are not allowed");

        assertThat(bookingRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void bookingServices_priceAtBookingIsSnapshot() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        LocalDate date = fixtures.nextDateForDay(0);
        fixtures.saveSchedule(barber, 0, LocalTime.of(8, 0), LocalTime.of(18, 0), false);

        // Execute: create booking, then change the current service price.
        BookingResponse created = bookingService.createBooking(
                customer, barber.getId(), date, LocalTime.of(9, 0), List.of(service.getId())
        );
        service.setPrice(new BigDecimal("175000"));
        serviceRepository.saveAndFlush(service);

        // Assert: BookingService stores the original price snapshot.
        entityManager.flush();
        entityManager.clear();
        Booking persisted = bookingRepository.findById(created.id()).orElseThrow();
        assertThat(serviceRepository.findById(service.getId()).orElseThrow().getPrice())
                .isEqualByComparingTo("175000");
        assertThat(persisted.getBookingServices()).hasSize(1);
        assertThat(persisted.getBookingServices().get(0).getPriceAtBooking())
                .isEqualByComparingTo("100000");
        assertThat(persisted.getTotalPrice()).isEqualByComparingTo("100000");
    }

    @Test
    void bookingPagination_returnsCorrectPage() {
        // Arrange
        User customer = fixtures.customerA();
        User barber = fixtures.barberA();
        Service service = fixtures.serviceA();
        LocalDate firstDate = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        for (int i = 0; i < 12; i++) {
            LocalDate date = firstDate.plusDays(i);
            LocalTime start = LocalTime.of(8, 0).plusMinutes(i * 30L);
            fixtures.saveBooking(
                    customer, barber, date, start, start.plusMinutes(30),
                    BookingStatus.PENDING, service
            );
        }

        // Execute
        PageResponse<BookingResponse> first = bookingService.getAllBookings(
                customer, 1, 10, null, null, null, null
        );
        PageResponse<BookingResponse> second = bookingService.getAllBookings(
                customer, 2, 10, null, null, null, null
        );

        // Assert
        assertThat(first.items()).hasSize(10);
        assertThat(second.items()).hasSize(2);
        assertThat(first.pageInfo().total()).isEqualTo(12);
        assertThat(first.pageInfo().page()).isEqualTo(1);
        assertThat(second.pageInfo().page()).isEqualTo(2);
        assertThat(first.pageInfo().limit()).isEqualTo(10);
        assertThat(first.pageInfo().pages()).isEqualTo(2);

        Set<Integer> firstIds = new HashSet<>(
                first.items().stream().map(BookingResponse::id).toList()
        );
        Set<Integer> secondIds = new HashSet<>(
                second.items().stream().map(BookingResponse::id).toList()
        );
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
        assertThat(first.items().get(0).bookingDate())
                .isAfter(first.items().get(1).bookingDate());
    }
}
