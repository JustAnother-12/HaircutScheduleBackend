package com.j2ee.HaircutSchedule;

import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class HaircutScheduleApplication {

	public static void main(String[] args) {
		SpringApplication.run(HaircutScheduleApplication.class, args);
	}

	@Bean
	CommandLineRunner initDatabase(UserRepository userRepository) {
		return args -> {
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
			}
		};
	}

}
