package com.j2ee.HaircutSchedule.integration;

import com.j2ee.HaircutSchedule.config.PostgresTestConfiguration;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.exception.ResourceAlreadyExistedException;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import com.j2ee.HaircutSchedule.service.UserService;
import com.j2ee.HaircutSchedule.support.TestDataFactory;
import com.j2ee.HaircutSchedule.support.TestFixtureConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import({
        PostgresTestConfiguration.class,
        TestFixtureConfiguration.class
})
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestDataFactory fixtures;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminUsers_shouldIncludeInactiveUsersWhenNoActiveFilter() {
        var active = fixtures.customerA();

        var inactive = fixtures.saveUser(
                "Inactive customer",
                "inactive@example.com",
                com.j2ee.HaircutSchedule.enums.UserRole.CUSTOMER,
                false
        );

        var result = userService.getUsers(
                1,
                10,
                null,
                null,
                null,
                null
        );

        assertThat(result.items())
                .extracting(UserResponse::id)
                .contains(active.getId(), inactive.getId());
    }

    @Test
    void publicBarbers_shouldRespectOptionalActiveFilter() {
        var active = fixtures.barberA();

        var inactive = fixtures.saveUser(
                "Inactive barber",
                "inactive-barber@example.com",
                com.j2ee.HaircutSchedule.enums.UserRole.BARBER,
                false
        );

        var result = userService.getBarbers(
                1,
                10,
                null,
                null,
                null,
                null
        );

        assertThat(result.items())
                .extracting(UserResponse::id)
                .contains(active.getId(), inactive.getId());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminUsers_filterByRole() {
        // Arrange
        fixtures.customerA();
        User barber = fixtures.barberA();
        fixtures.admin();

        // Execute
        PageResponse<UserResponse> result = userService.getUsers(
                1, 10, null, null, null, UserRole.BARBER
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.items()).allMatch(item -> item.role() == UserRole.BARBER);
        assertThat(result.items().get(0).id()).isEqualTo(barber.getId());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminUsers_filterFullNamePartial() {
        // Arrange
        User matching = fixtures.saveUser(
                "Nguyen Van An", "an@example.com", UserRole.CUSTOMER, true
        );
        fixtures.saveUser("Tran Van Binh", "binh@example.com", UserRole.CUSTOMER, true);
        fixtures.saveUser("Nguyen Thi Hoa", "hoa@example.com", UserRole.CUSTOMER, true);

        // Execute
        PageResponse<UserResponse> result = userService.getUsers(
                1, 10, " nGuYeN vAn ", null, null, null
        );

        // Assert
        assertThat(result.items())
                .extracting(UserResponse::id)
                .containsExactly(matching.getId());
        assertThat(result.pageInfo().total()).isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminUsers_filterEmailExactCaseInsensitive() {
        // Arrange
        User matching = fixtures.saveUser(
                "Email Match", "customer.email@example.com", UserRole.CUSTOMER, true
        );
        fixtures.saveUser("Other Email", "other@example.com", UserRole.CUSTOMER, true);

        // Execute
        PageResponse<UserResponse> result = userService.getUsers(
                1, 10, null, "  CUSTOMER.EMAIL@EXAMPLE.COM  ", null, null
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(matching.getId());
        assertThat(result.items().get(0).email())
                .isEqualTo("customer.email@example.com");
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminUsers_combinesAllFilters() {
        // Arrange
        User matching = fixtures.saveUser(
                "Target Customer", "target@example.com", UserRole.CUSTOMER, true
        );
        User wrongRole = fixtures.saveUser(
                "Target Barber", "target.barber@example.com", UserRole.BARBER, true
        );
        User wrongName = fixtures.saveUser(
                "Different Customer", "target.other@example.com", UserRole.CUSTOMER, true
        );
        User wrongEmail = fixtures.saveUser(
                "Target Other", "other@example.com", UserRole.CUSTOMER, true
        );

        // Execute
        PageResponse<UserResponse> result = userService.getUsers(
                1,
                10,
                "TARGET CUSTOMER",
                "TARGET@EXAMPLE.COM",
                matching.getPhone(),
                UserRole.CUSTOMER
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(matching.getId());
        assertThat(result.items().get(0).role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(result.items().get(0).fullName()).isEqualTo("Target Customer");
        assertThat(result.items().get(0).email()).isEqualTo("target@example.com");
        assertThat(result.items().get(0).phone()).isEqualTo(matching.getPhone());
        assertThat(result.items())
                .extracting(UserResponse::id)
                .doesNotContain(wrongRole.getId(), wrongName.getId(), wrongEmail.getId());
    }

    @Test
    void barbers_onlyReturnsBarberRole() {
        // Arrange
        User barberA = fixtures.barberA();
        User barberB = fixtures.barberB();
        fixtures.customerA();
        fixtures.admin();

        // Execute
        PageResponse<UserResponse> result = userService.getBarbers(
                1, 10, null, null, null, null
        );

        // Assert
        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).allMatch(item -> item.role() == UserRole.BARBER);
        assertThat(result.items())
                .extracting(UserResponse::id)
                .containsExactlyInAnyOrder(barberA.getId(), barberB.getId());
    }

    @Test
    void barbers_activeFilterFalse() {
        // Arrange
        User inactive = fixtures.saveUser(
                "Inactive Barber", "inactive.barber@example.com", UserRole.BARBER, false
        );
        fixtures.barberA();

        // Execute
        PageResponse<UserResponse> result = userService.getBarbers(
                1, 10, null, null, null, false
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(inactive.getId());
        assertThat(result.items()).allMatch(item -> !item.isActive());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void createUser_persistsEncodedPassword() {
        // Arrange
        String rawPassword = "Password123";

        // Execute
        UserResponse result = userService.createUser(
                "Created Customer",
                "created.customer@example.com",
                "0912345678",
                rawPassword,
                UserRole.CUSTOMER
        );

        // Assert
        entityManager.flush();
        entityManager.clear();

        User persisted = userRepository.findById(result.id()).orElseThrow();
        assertThat(persisted.getFullName()).isEqualTo("Created Customer");
        assertThat(persisted.getEmail()).isEqualTo("created.customer@example.com");
        assertThat(persisted.getPhone()).isEqualTo("0912345678");
        assertThat(persisted.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(persisted.isActive()).isTrue();
        assertThat(persisted.getHashedPassword()).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, persisted.getHashedPassword()))
                .isTrue();
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void createUser_duplicateEmailRejected() {
        // Arrange
        fixtures.saveUser(
                "Existing Customer", "duplicate.email@example.com", UserRole.CUSTOMER, true
        );
        long countBefore = userRepository.count();

        // Execute & Assert
        assertThatThrownBy(() -> userService.createUser(
                "Another Customer",
                "DUPLICATE.EMAIL@example.com",
                "0912345679",
                "Password123",
                UserRole.CUSTOMER
        )).isInstanceOf(ResourceAlreadyExistedException.class);

        assertThat(userRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void createUser_duplicatePhoneRejected() {
        // Arrange
        User existing = fixtures.saveUser(
                "Existing Customer", "existing.phone@example.com", UserRole.CUSTOMER, true
        );
        long countBefore = userRepository.count();

        // Execute & Assert
        assertThatThrownBy(() -> userService.createUser(
                "Another Customer",
                "another.phone@example.com",
                existing.getPhone(),
                "Password123",
                UserRole.CUSTOMER
        )).isInstanceOf(ResourceAlreadyExistedException.class);

        assertThat(userRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void updateUser_partialFieldsPersisted() {
        // Arrange
        User user = fixtures.customerA();
        String previousEmail = user.getEmail();
        String previousPhone = user.getPhone();

        // Execute
        UserResponse updated = userService.updateUser(
                user.getId(),
                ArgumentValue.ofNullable("Updated Customer Name"),
                ArgumentValue.omitted(),
                ArgumentValue.omitted(),
                ArgumentValue.omitted()
        );

        // Assert: force SQL synchronization and read the entity again.
        entityManager.flush();
        entityManager.clear();

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.fullName()).isEqualTo("Updated Customer Name");
        assertThat(persisted.getFullName()).isEqualTo("Updated Customer Name");
        assertThat(persisted.getEmail()).isEqualTo(previousEmail);
        assertThat(persisted.getPhone()).isEqualTo(previousPhone);
        assertThat(persisted.getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminUsers_filterPhone() {
        // Arrange
        User matching = fixtures.customerA();
        fixtures.customerB();

        // Execute
        PageResponse<UserResponse> result = userService.getUsers(
                1, 10, null, null, matching.getPhone(), null
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(matching.getId());
        assertThat(result.items().get(0).phone()).isEqualTo(matching.getPhone());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void users_pagination_returnsCorrectPage() {
        // Arrange
        for (int i = 1; i <= 12; i++) {
            fixtures.saveUser(
                    String.format("Pagination User %02d", i),
                    String.format("pagination.user%02d@example.com", i),
                    UserRole.CUSTOMER,
                    true
            );
        }

        // Execute
        PageResponse<UserResponse> firstPage = userService.getUsers(
                1, 10, "Pagination User", null, null, UserRole.CUSTOMER
        );
        PageResponse<UserResponse> secondPage = userService.getUsers(
                2, 10, "Pagination User", null, null, UserRole.CUSTOMER
        );

        // Assert
        assertThat(firstPage.items()).hasSize(10);
        assertThat(secondPage.items()).hasSize(2);
        assertThat(firstPage.pageInfo().total()).isEqualTo(12);
        assertThat(firstPage.pageInfo().page()).isEqualTo(1);
        assertThat(secondPage.pageInfo().page()).isEqualTo(2);
        assertThat(firstPage.pageInfo().limit()).isEqualTo(10);
        assertThat(firstPage.pageInfo().pages()).isEqualTo(2);

        Set<Integer> firstIds = new HashSet<>(
                firstPage.items().stream().map(UserResponse::id).toList()
        );
        Set<Integer> secondIds = new HashSet<>(
                secondPage.items().stream().map(UserResponse::id).toList()
        );
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void updateUser_duplicateEmailRejected() {
        // Arrange
        User owner = fixtures.customerA();
        User another = fixtures.customerB();
        String originalEmail = owner.getEmail();

        // Execute & Assert
        assertThatThrownBy(() -> userService.updateUser(
                owner.getId(),
                ArgumentValue.omitted(),
                ArgumentValue.ofNullable(another.getEmail()),
                ArgumentValue.omitted(),
                ArgumentValue.omitted()
        )).isInstanceOf(ResourceAlreadyExistedException.class)
                .hasMessage("Email already exists");

        // The rejected update must not change the original record.
        entityManager.clear();
        User unchanged = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(unchanged.getEmail()).isEqualTo(originalEmail);
    }
}
