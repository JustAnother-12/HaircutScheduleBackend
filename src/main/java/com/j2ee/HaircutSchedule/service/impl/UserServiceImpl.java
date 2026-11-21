package com.j2ee.HaircutSchedule.service.impl;


import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.exception.ResourceAlreadyExistedException;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.mapper.UserMapper;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import com.j2ee.HaircutSchedule.service.UserService;
import com.j2ee.HaircutSchedule.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.j2ee.HaircutSchedule.util.Helpers.validateArgumentStringNotBlankOrNull;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse updateCurrentUserProfile(
            User currentUser,
            ArgumentValue<String> fullName,
            ArgumentValue<String> email,
            ArgumentValue<String> phone
    ) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        if (ValidationUtils.isProvided(fullName)) {
            user.setFullName(
                    ValidationUtils.requireFullName(fullName.value())
            );
        }

        if (ValidationUtils.isProvided(email)) {
            String validEmail = ValidationUtils.requireEmail(email.value());

            boolean emailUsed = userRepository
                    .findByEmailIgnoreCase(validEmail)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .isPresent();

            if (emailUsed) {
                throw new ResourceAlreadyExistedException(
                        "Email already exists"
                );
            }

            user.setEmail(validEmail);
        }

        if (ValidationUtils.isProvided(phone)) {
            String validPhone = ValidationUtils.requirePhone(phone.value());

            boolean phoneUsed = userRepository.findByPhone(validPhone)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .isPresent();

            if (phoneUsed) {
                throw new ResourceAlreadyExistedException(
                        "Phone already exists"
                );
            }

            user.setPhone(validPhone);
        }

        return userMapper.toResponse(currentUser);
    }

    @Override
    @Transactional
    public Boolean updateCurrentUserPassword(
            User currentUser,
            ArgumentValue<String> oldPassword,
            ArgumentValue<String> password
    ) {
        if (!ValidationUtils.isProvided(oldPassword)
                || !ValidationUtils.isProvided(password)) {
            throw new IllegalArgumentException(
                    "Old password and new password are required"
            );
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        String oldValue = ValidationUtils.requireText(
                oldPassword.value(),
                "Old password",
                1,
                255
        );

        String newValue = ValidationUtils.requirePassword(password.value());

        if (user.getHashedPassword() == null || !passwordEncoder.matches(oldValue, user.getHashedPassword())) {
            throw new IllegalArgumentException(
                    "Invalid old password"
            );
        }

        if (passwordEncoder.matches(newValue, user.getHashedPassword())) {
            throw new IllegalArgumentException(
                    "New password must be different from old password"
            );
        }

        user.setHashedPassword(
                passwordEncoder.encode(newValue)
        );

        return true;
    }

    @Override
    public UserResponse getCurrentUser(User currentUser) {
        return userMapper.toResponse(currentUser);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    public List<UserResponse> getAllBarbers() {
        return userRepository.findAllByRole(UserRole.BARBER)
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    public UserResponse getUserById(Integer id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElse(null);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    public UserResponse createUser(
            String fullName,
            String email,
            String phone,
            String password,
            UserRole role
    ) {
        String validName = ValidationUtils.requireFullName(fullName);
        String validEmail = ValidationUtils.requireEmail(email);
        String validPhone = ValidationUtils.requirePhone(phone);
        String validPassword = ValidationUtils.requirePassword(password);

        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }

        if (userRepository.findByEmailIgnoreCase(validEmail).isPresent()
                || userRepository.findByPhone(validPhone).isPresent()) {
            throw new ResourceAlreadyExistedException(
                    "Email or phone already exists"
            );
        }

        User newUser = User.builder()
                .fullName(validName)
                .email(validEmail)
                .phone(validPhone)
                .role(role)
                .hashedPassword(passwordEncoder.encode(validPassword))
                .isActive(true)
                .build();

        return userMapper.toResponse(userRepository.saveAndFlush(newUser));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    public UserResponse updateUser(
            Integer userId,
            ArgumentValue<String> fullName,
            ArgumentValue<String> email,
            ArgumentValue<String> phone,
            ArgumentValue<UserRole> role
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find user with id: " + userId));

        boolean changed = false;

        if (ValidationUtils.isProvided(fullName)){
            user.setFullName(ValidationUtils.requireFullName(fullName.value()));
            changed = true;
        }

        if (ValidationUtils.isProvided(email)){
            user.setEmail(ValidationUtils.requireEmail(email.value()));
            changed = true;
        }

        if (ValidationUtils.isProvided(phone)){
            user.setPhone(ValidationUtils.requirePhone(phone.value()));
            changed = true;
        }

        if (ValidationUtils.isProvided(role)){
            if (role.value() == null) {
                throw new IllegalArgumentException("Role is required");
            }
            user.setRole(role.value());
            changed = true;
        }

        if (!changed) {
            throw new IllegalArgumentException(
                    "At least one field must be provided"
            );
        }


        return userMapper.toResponse(user);
    }
}
