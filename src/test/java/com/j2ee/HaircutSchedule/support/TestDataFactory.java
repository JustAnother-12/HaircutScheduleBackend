package com.j2ee.HaircutSchedule.support;

import com.j2ee.HaircutSchedule.entity.BarberSchedule;
import com.j2ee.HaircutSchedule.entity.Booking;
import com.j2ee.HaircutSchedule.entity.BookingService;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.repository.BarberScheduleRepository;
import com.j2ee.HaircutSchedule.repository.BookingRepository;
import com.j2ee.HaircutSchedule.repository.ServiceRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TestDataFactory {

    private static final AtomicInteger PHONE_SEQUENCE =
            new AtomicInteger(1);

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final BarberScheduleRepository scheduleRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataFactory(
            UserRepository userRepository,
            ServiceRepository serviceRepository,
            BarberScheduleRepository scheduleRepository,
            BookingRepository bookingRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.scheduleRepository = scheduleRepository;
        this.bookingRepository = bookingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // USERS
    public User saveUser(
            String fullName,
            String email,
            UserRole role,
            boolean active
    ) {
        String phone = "09" + String.format(
                "%08d",
                PHONE_SEQUENCE.getAndIncrement()
        );

        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .hashedPassword(
                        passwordEncoder.encode("customerpassword")
                )
                .role(role)
                .isActive(active)
                .build();

        return userRepository.saveAndFlush(user);
    }

    public User customerA() {
        return saveUser(
                "Test customerA",
                "customera@example.com",
                UserRole.CUSTOMER,
                true
        );
    }

    public User customerB() {
        return saveUser(
                "Test customerB",
                "customerb@example.com",
                UserRole.CUSTOMER,
                true
        );
    }

    public User barberA() {
        return saveUser(
                "Test barberA",
                "barbera@example.com",
                UserRole.BARBER,
                true
        );
    }

    public User barberB() {
        return saveUser(
                "Test barberB",
                "barberb@example.com",
                UserRole.BARBER,
                true
        );
    }

    public User admin() {
        return saveUser(
                "Test admin",
                "admin@example.com",
                UserRole.ADMIN,
                true
        );
    }

    // SERVICES
    public com.j2ee.HaircutSchedule.entity.Service saveService(
            String name,
            String description,
            BigDecimal price,
            int durationMinutes,
            boolean active
    ) {
        var service = com.j2ee.HaircutSchedule.entity.Service.builder()
                .name(name)
                .description(description)
                .price(price)
                .durationMinutes(durationMinutes)
                .isActive(active)
                .build();

        return serviceRepository.saveAndFlush(service);
    }

    public com.j2ee.HaircutSchedule.entity.Service serviceA() {
        return saveService(
                "Cắt tóc nam",
                "Cắt tóc nam cơ bản",
                new BigDecimal("100000"),
                30,
                true
        );
    }

    public com.j2ee.HaircutSchedule.entity.Service serviceB() {
        return saveService(
                "Cắt tóc nữ",
                "Cắt tóc nữ cơ bản",
                new BigDecimal("120000"),
                45,
                true
        );
    }

    public com.j2ee.HaircutSchedule.entity.Service inactiveService() {
        return saveService(
                "Cắt tóc trẻ em",
                null,
                new BigDecimal("80000"),
                20,
                false
        );
    }

    // BARBER SCHEDULES
    public BarberSchedule saveSchedule(
            User barber,
            int dateOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            boolean isOff
    ) {
        BarberSchedule schedule = BarberSchedule.builder()
                .barber(barber)
                .dateOfWeek(dateOfWeek)
                .startTime(startTime)
                .endTime(endTime)
                .isOff(isOff)
                .build();

        return scheduleRepository.saveAndFlush(schedule);
    }

    public List<BarberSchedule> saveFullWeek(
            User barber,
            LocalTime startTime,
            LocalTime endTime
    ) {
        return java.util.stream.IntStream.range(0, 7)
                .mapToObj(day -> saveSchedule(
                        barber,
                        day,
                        startTime,
                        endTime,
                        false
                ))
                .toList();
    }

    // BOOKINGS
    public Booking saveBooking(
            User customer,
            User barber,
            LocalDate bookingDate,
            LocalTime startTime,
            LocalTime endTime,
            BookingStatus status,
            com.j2ee.HaircutSchedule.entity.Service... services
    ) {
        BigDecimal totalPrice = Arrays.stream(services)
                .map(com.j2ee.HaircutSchedule.entity.Service::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Booking booking = Booking.builder()
                .customer(customer)
                .barber(barber)
                .bookingDate(bookingDate)
                .startTime(startTime)
                .endTime(endTime)
                .totalPrice(totalPrice)
                .status(status)
                .build();

        for (var service : services) {
            booking.addBookingService(
                    BookingService.builder()
                            .service(service)
                            .priceAtBooking(service.getPrice())
                            .build()
            );
        }

        return bookingRepository.saveAndFlush(booking);
    }

    // DATE HELPER
    // Monday = 0, Sunday = 6
    public LocalDate nextDateForDay(int dateOfWeek) {
        if (dateOfWeek < 0 || dateOfWeek > 6) {
            throw new IllegalArgumentException(
                    "dateOfWeek must be between 0 and 6"
            );
        }

        LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(1);

        while (date.getDayOfWeek().getValue() - 1 != dateOfWeek) {
            date = date.plusDays(1);
        }

        return date;
    }
}

