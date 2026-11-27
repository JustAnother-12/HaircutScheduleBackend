
package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.event.PasswordChangedEvent;
import com.j2ee.HaircutSchedule.mapper.UserMapper;
import com.j2ee.HaircutSchedule.repository.SessionRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import com.j2ee.HaircutSchedule.service.impl.UserServiceImpl;
import com.j2ee.HaircutSchedule.exception.ResourceAlreadyExistedException;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private Clock clock;

    @InjectMocks
    private UserServiceImpl userService;

    private User createCustomer() {
        return User.builder()
                .id(1)
                .fullName("Test customer")
                .email("customer@example.com")
                .phone("0901234567")
                .hashedPassword("old-hash")
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();
    }

    private UserResponse mapUser(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .isActive(user.isActive())
                .build();
    }

    @Test
    void updateProfile_onlyFullName_success() {
        User user = createCustomer();

        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        when(userMapper.toResponse(user))
                .thenReturn(
                        UserResponse.builder()
                                .id(1)
                                .fullName("Updated customer")
                                .email(user.getEmail())
                                .phone(user.getPhone())
                                .role(UserRole.CUSTOMER)
                                .isActive(true)
                                .build()
                );

        UserResponse response = userService.updateCurrentUserProfile(
                user,
                ArgumentValue.ofNullable("Updated customer"),
                ArgumentValue.omitted(),
                ArgumentValue.omitted()
        );

        assertThat(user.getFullName()).isEqualTo("Updated customer");
        assertThat(user.getEmail()).isEqualTo("customer@example.com");
        assertThat(response.fullName()).isEqualTo("Updated customer");
    }

    @Test
    void updateProfile_duplicateEmail_throwsException() {
        User user = createCustomer();

        User anotherUser = User.builder()
                .id(2)
                .email("new@example.com")
                .build();

        when(userRepository.findById(1))
                .thenReturn(Optional.of(user));

        when(userRepository.findByEmailIgnoreCase("new@example.com"))
                .thenReturn(Optional.of(anotherUser));

        assertThatThrownBy(() ->
                userService.updateCurrentUserProfile(
                        user,
                        ArgumentValue.omitted(),
                        ArgumentValue.ofNullable("new@example.com"),
                        ArgumentValue.omitted()
                )
        ).isInstanceOf(
                com.j2ee.HaircutSchedule.exception.ResourceAlreadyExistedException.class
        );

        assertThat(user.getEmail()).isEqualTo("customer@example.com");
    }

    @Test
    void updatePassword_validOldPassword_success() {
        User user = createCustomer();

        when(userRepository.findById(1))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("oldpassword", "old-hash"))
                .thenReturn(true);

        when(passwordEncoder.matches("newpassword", "old-hash"))
                .thenReturn(false);

        when(passwordEncoder.encode("newpassword"))
                .thenReturn("new-hash");

        when(clock.instant())
                .thenReturn(Instant.parse("2026-11-22T00:00:00Z"));

        when(sessionRepository.findAllByUserAndRevokedAtIsNull(user))
                .thenReturn(List.of());

        Boolean result = userService.updateCurrentUserPassword(
                user,
                ArgumentValue.ofNullable("oldpassword"),
                ArgumentValue.ofNullable("newpassword")
        );

        assertThat(result).isTrue();
        assertThat(user.getHashedPassword()).isEqualTo("new-hash");

        verify(sessionRepository).findAllByUserAndRevokedAtIsNull(user);
        verify(eventPublisher).publishEvent(any(PasswordChangedEvent.class));
    }

    @Test
    void updatePassword_invalidOldPassword_throwsException() {
        User user = createCustomer();

        when(userRepository.findById(1))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("wrongpassword", "old-hash"))
                .thenReturn(false);

        assertThatThrownBy(() ->
                userService.updateCurrentUserPassword(
                        user,
                        ArgumentValue.ofNullable("wrongpassword"),
                        ArgumentValue.ofNullable("newpassword")
                )
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid old password");

        assertThat(user.getHashedPassword()).isEqualTo("old-hash");

        verify(passwordEncoder, never()).encode("newpassword");
        verifyNoInteractions(sessionRepository, eventPublisher);
    }

    @Test
    void updateProfile_updateAllFields_success() {
        // Arrange
        User user = createCustomer();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        when(userRepository.findByEmailIgnoreCase("newcustomer@example.com"))
                .thenReturn(Optional.empty());

        when(userRepository.findByPhone("0912345678"))
                .thenReturn(Optional.empty());

        when(userMapper.toResponse(user))
                .thenAnswer(invocation ->
                        mapUser(invocation.getArgument(0))
                );

        // Execute
        UserResponse result = userService.updateCurrentUserProfile(
                user,
                ArgumentValue.ofNullable("Updated Customer"),
                ArgumentValue.ofNullable("  NewCustomer@Example.com  "),
                ArgumentValue.ofNullable("0912345678")
        );

        // Assert
        assertThat(user.getFullName()).isEqualTo("Updated Customer");
        assertThat(user.getEmail()).isEqualTo("newcustomer@example.com");
        assertThat(user.getPhone()).isEqualTo("0912345678");

        assertThat(result.fullName()).isEqualTo("Updated Customer");
        assertThat(result.email()).isEqualTo("newcustomer@example.com");
        assertThat(result.phone()).isEqualTo("0912345678");

        verify(userRepository).findByEmailIgnoreCase("newcustomer@example.com");
        verify(userRepository).findByPhone("0912345678");
        verify(userMapper).toResponse(user);
    }

    @Test
    void updateProfile_duplicatePhone_throwsException() {
        // Arrange
        User user = createCustomer();

        User anotherUser = User.builder()
                .id(2)
                .fullName("Another Customer")
                .email("another@example.com")
                .phone("0912345678")
                .build();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        when(userRepository.findByPhone("0912345678"))
                .thenReturn(Optional.of(anotherUser));

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserProfile(
                        user,
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.ofNullable("0912345678")
                )
        )
                .isInstanceOf(ResourceAlreadyExistedException.class)
                .hasMessage("Phone already exists");

        // Existing phone must remain unchanged.
        assertThat(user.getPhone()).isEqualTo("0901234567");

        verify(userMapper, never()).toResponse(any(User.class));
    }

    @Test
    void updateProfile_invalidFullName_throwsException() {
        // Arrange
        User user = createCustomer();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserProfile(
                        user,
                        ArgumentValue.ofNullable("A"),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted()
                )
        )
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(user.getFullName()).isEqualTo("Test customer");

        verify(userMapper, never()).toResponse(any(User.class));
    }

    @Test
    void updateProfile_invalidEmail_throwsException() {
        // Arrange
        User user = createCustomer();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserProfile(
                        user,
                        ArgumentValue.omitted(),
                        ArgumentValue.ofNullable("invalid-email"),
                        ArgumentValue.omitted()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid email format");

        assertThat(user.getEmail())
                .isEqualTo("customer@example.com");

        verify(userRepository, never())
                .findByEmailIgnoreCase(anyString());

        verify(userMapper, never()).toResponse(any(User.class));
    }

    @Test
    void updateProfile_userNotFound_throwsException() {
        // Arrange
        User user = createCustomer();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.empty());

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserProfile(
                        user,
                        ArgumentValue.ofNullable("Updated Customer"),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted()
                )
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");

        verify(userMapper, never()).toResponse(any(User.class));
    }

    @Test
    void updateProfile_sameEmailAndPhone_success() {
        // Arrange
        User user = createCustomer();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        when(userRepository.findByEmailIgnoreCase(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(userRepository.findByPhone(user.getPhone()))
                .thenReturn(Optional.of(user));

        when(userMapper.toResponse(user))
                .thenAnswer(invocation ->
                        mapUser(invocation.getArgument(0))
                );

        // Execute
        UserResponse result = userService.updateCurrentUserProfile(
                user,
                ArgumentValue.omitted(),
                ArgumentValue.ofNullable("CUSTOMER@EXAMPLE.COM"),
                ArgumentValue.ofNullable(user.getPhone())
        );

        // Assert
        assertThat(user.getEmail()).isEqualTo("customer@example.com");
        assertThat(user.getPhone()).isEqualTo("0901234567");

        assertThat(result.email()).isEqualTo("customer@example.com");
        assertThat(result.phone()).isEqualTo("0901234567");
    }

    @Test
    void updateProfile_invalidPhone_throwsException() {
        // Arrange
        User user = createCustomer();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserProfile(
                        user,
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.ofNullable("0123456789")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid phone number");

        assertThat(user.getPhone()).isEqualTo("0901234567");

        verify(userRepository, never()).findByPhone(anyString());
        verify(userMapper, never()).toResponse(any(User.class));
    }

    @Test
    void updateProfile_newPhoneTooShort_throwsException() {
        // Arrange
        User user = createCustomer();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserProfile(
                        user,
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.ofNullable("0123")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Phone must contain between 10 and 10 characters");

        assertThat(user.getPhone()).isEqualTo("0901234567");

        verify(userRepository, never()).findByPhone(anyString());
        verify(userMapper, never()).toResponse(any(User.class));
    }

    @Test
    void updateProfile_noFieldsProvided_rejected() {
        // Arrange
        User user = createCustomer();

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserProfile(
                        user,
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one field must be provided");

        verifyNoInteractions(userRepository, userMapper);
    }

    @Test
    void updatePassword_missingOldPassword_throwsException() {
        // Arrange
        User user = createCustomer();

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserPassword(
                        user,
                        ArgumentValue.omitted(),
                        ArgumentValue.ofNullable("newpassword")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Old password and new password are required");

        verifyNoInteractions(
                userRepository,
                passwordEncoder,
                sessionRepository,
                eventPublisher
        );
    }

    @Test
    void updatePassword_missingNewPassword_throwsException() {
        // Arrange
        User user = createCustomer();

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserPassword(
                        user,
                        ArgumentValue.ofNullable("oldpassword"),
                        ArgumentValue.omitted()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Old password and new password are required");

        verifyNoInteractions(
                userRepository,
                passwordEncoder,
                sessionRepository,
                eventPublisher
        );
    }

    @Test
    void updatePassword_newPasswordTooShort_throwsException() {
        // Arrange
        User user = createCustomer();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserPassword(
                        user,
                        ArgumentValue.ofNullable("oldpassword"),
                        ArgumentValue.ofNullable("short")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must contain between 8 and 32 characters");

        assertThat(user.getHashedPassword()).isEqualTo("old-hash");

        verifyNoInteractions(
                passwordEncoder,
                sessionRepository,
                eventPublisher
        );
    }

    @Test
    void updatePassword_newPasswordSameAsOld_throwsException() {
        // Arrange
        User user = createCustomer();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("oldpassword", "old-hash"))
                .thenReturn(true);

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserPassword(
                        user,
                        ArgumentValue.ofNullable("oldpassword"),
                        ArgumentValue.ofNullable("oldpassword")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("New password must be different from old password");

        assertThat(user.getHashedPassword()).isEqualTo("old-hash");

        verify(passwordEncoder, times(2))
                .matches("oldpassword", "old-hash");

        verify(passwordEncoder, never()).encode(anyString());

        verifyNoInteractions(sessionRepository, eventPublisher);
    }

    @Test
    void updatePassword_userHasNoPassword_throwsException() {
        // Arrange
        User user = createCustomer();
        user.setHashedPassword(null);

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserPassword(
                        user,
                        ArgumentValue.ofNullable("oldpassword"),
                        ArgumentValue.ofNullable("newpassword")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid old password");

        assertThat(user.getHashedPassword()).isNull();

        verifyNoInteractions(
                passwordEncoder,
                sessionRepository,
                eventPublisher
        );
    }

    @Test
    void updatePassword_userNotFound_throwsException() {
        // Arrange
        User user = createCustomer();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.empty());

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateCurrentUserPassword(
                        user,
                        ArgumentValue.ofNullable("oldpassword"),
                        ArgumentValue.ofNullable("newpassword")
                )
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");

        verify(userRepository).findById(user.getId());

        verifyNoInteractions(
                passwordEncoder,
                sessionRepository,
                eventPublisher
        );
    }

    @Test
    void createUser_validData_success() {
        // Arrange
        when(userRepository.findByEmailIgnoreCase("newcustomer@example.com"))
                .thenReturn(Optional.empty());

        when(userRepository.findByPhone("0912345678"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode("Password123"))
                .thenReturn("encoded-password");

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> {
                    User saved = invocation.getArgument(0);
                    saved.setId(10);
                    return saved;
                });

        when(userMapper.toResponse(any(User.class)))
                .thenAnswer(invocation ->
                        mapUser(invocation.getArgument(0))
                );

        // Execute
        UserResponse result = userService.createUser(
                "New Customer",
                "NewCustomer@Example.com",
                "0912345678",
                "Password123",
                UserRole.CUSTOMER
        );

        // Assert
        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).saveAndFlush(captor.capture());

        User saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo(10);
        assertThat(saved.getFullName()).isEqualTo("New Customer");
        assertThat(saved.getEmail()).isEqualTo("newcustomer@example.com");
        assertThat(saved.getPhone()).isEqualTo("0912345678");
        assertThat(saved.getHashedPassword()).isEqualTo("encoded-password");
        assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(saved.isActive()).isTrue();

        assertThat(result.id()).isEqualTo(10);
        assertThat(result.email()).isEqualTo("newcustomer@example.com");

        verify(passwordEncoder).encode("Password123");
    }

    @Test
    void createUser_duplicatePhone_throwsException() {
        // Arrange
        User existingUser = User.builder()
                .id(2)
                .email("another@example.com")
                .phone("0912345678")
                .build();

        when(userRepository.findByEmailIgnoreCase("newcustomer@example.com"))
                .thenReturn(Optional.empty());

        when(userRepository.findByPhone("0912345678"))
                .thenReturn(Optional.of(existingUser));

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.createUser(
                        "New Customer",
                        "newcustomer@example.com",
                        "0912345678",
                        "Password123",
                        UserRole.CUSTOMER
                )
        )
                .isInstanceOf(ResourceAlreadyExistedException.class)
                .hasMessage("Email or phone already exists");

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(passwordEncoder, userMapper);
    }

    @Test
    void createUser_nullRole_throwsException() {
        // Execute & Assert
        assertThatThrownBy(() ->
                userService.createUser(
                        "New Customer",
                        "newcustomer@example.com",
                        "0912345678",
                        "Password123",
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role is required");

        verifyNoInteractions(
                userRepository,
                passwordEncoder,
                userMapper
        );
    }

    @Test
    void updateUser_partialUpdate_success() {
        // Arrange
        User user = createCustomer();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        when(userMapper.toResponse(user))
                .thenAnswer(invocation ->
                        mapUser(invocation.getArgument(0))
                );

        // Execute
        UserResponse result = userService.updateUser(
                user.getId(),
                ArgumentValue.ofNullable("Updated Customer"),
                ArgumentValue.omitted(),
                ArgumentValue.omitted(),
                ArgumentValue.ofNullable(UserRole.BARBER)
        );

        // Assert
        assertThat(user.getFullName()).isEqualTo("Updated Customer");
        assertThat(user.getEmail()).isEqualTo("customer@example.com");
        assertThat(user.getPhone()).isEqualTo("0901234567");
        assertThat(user.getRole()).isEqualTo(UserRole.BARBER);

        assertThat(result.fullName()).isEqualTo("Updated Customer");
        assertThat(result.role()).isEqualTo(UserRole.BARBER);
    }

    @Test
    void updateUser_duplicateEmailOrPhone_throwsException() {
        // Arrange
        User userWithDuplicateEmail = createCustomer();

        User userWithDuplicatePhone = createCustomer();
        userWithDuplicatePhone.setId(3);

        User anotherUser = User.builder()
                .id(2)
                .fullName("Another Customer")
                .email("taken@example.com")
                .phone("0912345678")
                .build();

        when(userRepository.findById(1))
                .thenReturn(Optional.of(userWithDuplicateEmail));

        when(userRepository.findByEmailIgnoreCase("taken@example.com"))
                .thenReturn(Optional.of(anotherUser));

        when(userRepository.findById(3))
                .thenReturn(Optional.of(userWithDuplicatePhone));

        when(userRepository.findByPhone("0912345678"))
                .thenReturn(Optional.of(anotherUser));

        // Execute & Assert: duplicate email
        assertThatThrownBy(() ->
                userService.updateUser(
                        1,
                        ArgumentValue.omitted(),
                        ArgumentValue.ofNullable("taken@example.com"),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted()
                )
        )
                .isInstanceOf(ResourceAlreadyExistedException.class)
                .hasMessage("Email already exists");

        // Execute & Assert: duplicate phone
        assertThatThrownBy(() ->
                userService.updateUser(
                        3,
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.ofNullable("0912345678"),
                        ArgumentValue.omitted()
                )
        )
                .isInstanceOf(ResourceAlreadyExistedException.class)
                .hasMessage("Phone already exists");

        // Existing data must remain unchanged.
        assertThat(userWithDuplicateEmail.getEmail())
                .isEqualTo("customer@example.com");

        assertThat(userWithDuplicatePhone.getPhone())
                .isEqualTo("0901234567");

        verify(userMapper, never()).toResponse(any(User.class));
    }

    @Test
    void updateUser_userNotFound_throwsException() {
        // Arrange
        when(userRepository.findById(999))
                .thenReturn(Optional.empty());

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateUser(
                        999,
                        ArgumentValue.ofNullable("Updated Customer"),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted()
                )
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cannot find user with id: 999");

        verify(userMapper, never()).toResponse(any(User.class));
    }

    @Test
    void updateUser_noFieldsProvided_throwsException() {
        // Arrange
        User user = createCustomer();

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        // Execute & Assert
        assertThatThrownBy(() ->
                userService.updateUser(
                        user.getId(),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted(),
                        ArgumentValue.omitted()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one field must be provided");

        verify(userMapper, never()).toResponse(any(User.class));
    }
}

