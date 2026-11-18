package com.j2ee.HaircutSchedule.config;

import com.j2ee.HaircutSchedule.entity.*;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {
    private static final int BARBER_COUNT = 20;

    private static final int CUSTOMER_COUNT = 50;

    private static final int HISTORICAL_BOOKING_DAYS = 14;

    private static final String ADMIN_PASSWORD = "Admin@123";

    private static final String BARBER_PASSWORD = "Barber@123";

    private static final String CUSTOMER_PASSWORD = "Customer@123";

    private final UserRepository userRepository;

    private final ServiceRepository serviceRepository;

    private final BarberScheduleRepository barberScheduleRepository;

    private final BookingRepository bookingRepository;

    private final PasswordEncoder passwordEncoder;


    @Override
    @Transactional
    public void run(String... args) {

        System.out.println("Starting database seeding...");

        // Do not insert sample data into a non-empty database.
        if (userRepository.count() > 0
                || serviceRepository.count() > 0) {

            System.out.println(
                    "Database already contains data. Skipping seeding."
            );

            return;
        }

        // 1. SERVICES
        List<Service> services = seedServices();

        List<Service> activeServices = services.stream()
                .filter(Service::isActive)
                .toList();


        // 2. ADMIN
        User admin = seedAdmin();


        // 3. BARBERS
        List<User> barbers = seedBarbers();


        // 4. CUSTOMERS
        List<User> customers = seedCustomers();


        // 5. BARBER SCHEDULES
        List<BarberSchedule> schedules =
                seedBarberSchedules(barbers);


        // 6. HISTORICAL BOOKINGS
        List<Booking> bookings = seedHistoricalBookings(
                barbers,
                customers,
                activeServices
        );


        // 7. SUMMARY
        printSummary(
                admin,
                barbers,
                customers,
                services,
                activeServices,
                schedules,
                bookings
        );
    }


    // =====================================================
    // 1. SERVICES
    // =====================================================

    private List<Service> seedServices() {

        System.out.println("Seeding services...");

        List<Service> services = List.of(

                Service.builder()
                        .name("Cắt tóc nam tiêu chuẩn")
                        .description("Cắt tóc, cạo mặt, vuốt sáp")
                        .price(new BigDecimal("70000.00"))
                        .durationMinutes(30)
                        .isActive(true)
                        .build(),

                Service.builder()
                        .name("Gội đầu thư giãn")
                        .description("Gội đầu massage 20 phút")
                        .price(new BigDecimal("40000.00"))
                        .durationMinutes(20)
                        .isActive(true)
                        .build(),

                Service.builder()
                        .name("Uốn tóc")
                        .description("Uốn phồng, uốn xoăn")
                        .price(new BigDecimal("250000.00"))
                        .durationMinutes(90)
                        .isActive(true)
                        .build(),

                Service.builder()
                        .name("Nhuộm tóc")
                        .description("Nhuộm màu thời trang")
                        .price(new BigDecimal("350000.00"))
                        .durationMinutes(120)
                        .isActive(true)
                        .build(),

                Service.builder()
                        .name("Hấp phục hồi tóc")
                        .description("Chăm sóc và phục hồi tóc hư tổn")
                        .price(new BigDecimal("150000.00"))
                        .durationMinutes(45)
                        .isActive(true)
                        .build(),

                Service.builder()
                        .name("Cạo râu")
                        .description("Cạo râu và chăm sóc da mặt")
                        .price(new BigDecimal("50000.00"))
                        .durationMinutes(20)
                        .isActive(true)
                        .build(),

                Service.builder()
                        .name("Cắt tóc trẻ em")
                        .description("Dịch vụ cắt tóc dành cho trẻ em")
                        .price(new BigDecimal("60000.00"))
                        .durationMinutes(30)
                        .isActive(true)
                        .build(),

                Service.builder()
                        .name("Dịch vụ ngừng kinh doanh")
                        .description(
                                "Dịch vụ dùng để kiểm tra lọc dịch vụ inactive"
                        )
                        .price(new BigDecimal("100000.00"))
                        .durationMinutes(30)
                        .isActive(false)
                        .build()
        );

        return serviceRepository.saveAll(services);
    }


    // =====================================================
    // 2. ADMIN
    // =====================================================

    private User seedAdmin() {

        System.out.println("Seeding admin...");

        User admin = createUser(
                "System Admin",
                "admin@salon.com",
                "0900000000",
                UserRole.ADMIN,
                ADMIN_PASSWORD
        );

        return userRepository.save(admin);
    }


    // =====================================================
    // 3. BARBERS
    // =====================================================

    private List<User> seedBarbers() {

        System.out.println(
                "Seeding " + BARBER_COUNT + " barbers..."
        );

        List<User> barbers = new ArrayList<>();

        for (int index = 1; index <= BARBER_COUNT; index++) {

            String fullName = String.format(
                    "Barber %03d",
                    index
            );

            String email = String.format(
                    "barber%03d@salon.test",
                    index
            );

            String phone = String.format(
                    "091%07d",
                    index
            );

            User barber = createUser(
                    fullName,
                    email,
                    phone,
                    UserRole.BARBER,
                    BARBER_PASSWORD
            );

            barbers.add(barber);
        }

        return userRepository.saveAll(barbers);
    }


    // =====================================================
    // 4. CUSTOMERS
    // =====================================================

    private List<User> seedCustomers() {

        System.out.println(
                "Seeding " + CUSTOMER_COUNT + " customers..."
        );

        List<User> customers = new ArrayList<>();

        for (int index = 1; index <= CUSTOMER_COUNT; index++) {

            String fullName = String.format(
                    "Customer %03d",
                    index
            );

            String email = String.format(
                    "customer%03d@salon.test",
                    index
            );

            String phone = String.format(
                    "093%07d",
                    index
            );

            User customer = createUser(
                    fullName,
                    email,
                    phone,
                    UserRole.CUSTOMER,
                    CUSTOMER_PASSWORD
            );

            customers.add(customer);
        }

        return userRepository.saveAll(customers);
    }


    // =====================================================
    // USER FACTORY
    // =====================================================

    private User createUser(
            String fullName,
            String email,
            String phone,
            UserRole role,
            String rawPassword
    ) {

        return User.builder()
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .hashedPassword(
                        passwordEncoder.encode(rawPassword)
                )
                .role(role)
                .isActive(true)
                .build();
    }


    // =====================================================
    // 5. BARBER SCHEDULES
    // =====================================================

    private List<BarberSchedule> seedBarberSchedules(
            List<User> barbers
    ) {

        System.out.println("Seeding barber schedules...");

        List<BarberSchedule> schedules = new ArrayList<>();

        for (User barber : barbers) {

            // Monday -> Sunday.
            for (int day = 0; day < 7; day++) {

                BarberSchedule schedule = BarberSchedule.builder()
                        .barber(barber)
                        .dateOfWeek(day)
                        .startTime(LocalTime.of(8, 0))
                        .endTime(LocalTime.of(20, 0))
                        .isOff(false)
                        .build();

                schedules.add(schedule);
            }
        }

        return barberScheduleRepository.saveAll(schedules);
    }


    // =====================================================
    // 6. HISTORICAL BOOKINGS
    // =====================================================

    private List<Booking> seedHistoricalBookings(
            List<User> barbers,
            List<User> customers,
            List<Service> activeServices
    ) {

        System.out.println(
                "Seeding historical bookings for "
                        + HISTORICAL_BOOKING_DAYS
                        + " days..."
        );

        List<Booking> bookings = new ArrayList<>();

        for (int dayOffset = 1;
             dayOffset <= HISTORICAL_BOOKING_DAYS;
             dayOffset++) {

            // All sample bookings are in the past.
            LocalDate bookingDate = LocalDate.now()
                    .minusDays(dayOffset);

            for (int customerIndex = 0;
                 customerIndex < customers.size();
                 customerIndex++) {

                User customer = customers.get(customerIndex);


                // Distribute customers among barbers.
                int barberIndex = customerIndex % BARBER_COUNT;

                User barber = barbers.get(barberIndex);


                // Each barber receives up to 3 bookings per day.
                // Start times: 09:00, 12:00, 15:00.

                int slotIndex = customerIndex / BARBER_COUNT;

                int startHour = 9 + slotIndex * 3;

                LocalTime startTime = LocalTime.of(
                        startHour,
                        0
                );


                // Distribute services among bookings.
                int serviceIndex = (
                        customerIndex + dayOffset
                ) % activeServices.size();

                Service service = activeServices.get(serviceIndex);


                // Calculate booking end time.
                LocalTime endTime = startTime.plusMinutes(
                        service.getDurationMinutes()
                );


                // Create booking.
                Booking booking = Booking.builder()
                        .customer(customer)
                        .barber(barber)
                        .bookingDate(bookingDate)
                        .startTime(startTime)
                        .endTime(endTime)
                        .totalPrice(service.getPrice())
                        .status(BookingStatus.COMPLETED)
                        .build();


                // Create booking service.
                BookingService bookingService = BookingService.builder()
                        .service(service)
                        .priceAtBooking(service.getPrice())
                        .build();


                // Set booking relationship.
                booking.addBookingService(bookingService);

                bookings.add(booking);
            }
        }

        return bookingRepository.saveAll(bookings);
    }


    // =====================================================
    // 7. SUMMARY
    // =====================================================

    private void printSummary(
            User admin,
            List<User> barbers,
            List<User> customers,
            List<Service> services,
            List<Service> activeServices,
            List<BarberSchedule> schedules,
            List<Booking> bookings
    ) {

        int bookingServiceCount = bookings.stream()
                .mapToInt(booking ->
                        booking.getBookingServices().size()
                )
                .sum();

        System.out.println("""

                =========================================
                DATABASE SEEDING COMPLETED
                =========================================

                Admin:
                    1

                Barbers:
                    %d

                Customers:
                    %d

                Services:
                    %d

                Active Services:
                    %d

                Barber Schedules:
                    %d

                Historical Bookings:
                    %d

                Booking Services:
                    %d

                =========================================
                """.formatted(
                barbers.size(),
                customers.size(),
                services.size(),
                activeServices.size(),
                schedules.size(),
                bookings.size(),
                bookingServiceCount
        ));
    }
}
