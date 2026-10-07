package com.j2ee.HaircutSchedule.config;

import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("dev") // Only run in dev env
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {
    private final UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Currently seeding Database...");
        if (userRepository.count() == 0) {
            User customer = User.builder()
                    .username("nguyenvana")
                    .fullName("Nguyễn Văn A")
                    .email("vana@gmail.com")
                    .phone("0901234567")
                    .role(UserRole.CUSTOMER)
                    .build();

            User barber = User.builder()
                    .username("transenb")
                    .fullName("Trần Sơn B")
                    .email("sonb.barber@gmail.com")
                    .phone("0988888888")
                    .role(UserRole.BARBER)
                    .build();

            userRepository.saveAll(List.of(customer, barber));
            System.out.println("Seeding finished!");
        }
    }
}
