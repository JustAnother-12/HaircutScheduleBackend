
package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.event.PasswordChangedEvent;
import com.j2ee.HaircutSchedule.mapper.UserMapper;
import com.j2ee.HaircutSchedule.repository.SessionRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import com.j2ee.HaircutSchedule.service.impl.UserServiceImpl;

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
}

