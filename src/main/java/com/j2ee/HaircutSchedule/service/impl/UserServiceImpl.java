package com.j2ee.HaircutSchedule.service.impl;


import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.event.PasswordChangedEvent;
import com.j2ee.HaircutSchedule.exception.ResourceAlreadyExistedException;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.mapper.UserMapper;
import com.j2ee.HaircutSchedule.repository.SessionRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import com.j2ee.HaircutSchedule.service.UserService;
import com.j2ee.HaircutSchedule.util.PageUtils;
import com.j2ee.HaircutSchedule.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.graphql.data.ArgumentValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

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

        return userMapper.toResponse(user);
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

        Instant now = clock.instant();

        sessionRepository.findAllByUserAndRevokedAtIsNull(user)
                .forEach(session ->
                        session.setRevokedAt(now)
                );

        eventPublisher.publishEvent(
                new PasswordChangedEvent(
                        user.getEmail(),
                        user.getFullName()
                )
        );

        return true;
    }

    @Override
    public UserResponse getCurrentUser(User currentUser) {
        return userMapper.toResponse(currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ADMIN')")
    public PageResponse<UserResponse> getUsers(
            Integer page,
            Integer limit,
            String fullName,
            String email,
            String phone,
            UserRole role
    ) {
        return searchUsers(
                page,
                limit,
                fullName,
                email,
                phone,
                role,
                null,
                false
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getBarbers(
            Integer page,
            Integer limit,
            String fullName,
            String email,
            String phone,
            Boolean isActive
    ) {
        return searchUsers(
                page,
                limit,
                fullName,
                email,
                phone,
                null,
                isActive,
                true
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getBarberById(Integer barberId) {
        User barber = userRepository.findByIdAndRole(
                barberId,
                UserRole.BARBER
        ).orElseThrow(() ->
                new ResourceNotFoundException(
                        "Cannot find barber with id: " + barberId
                )
        );

        return userMapper.toResponse(barber);
    }

    @Override
    public UserResponse getUserById(Integer id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot find User with id: " + id));
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
            changed = true;
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

    private PageResponse<UserResponse> searchUsers(
            Integer page,
            Integer limit,
            String fullName,
            String email,
            String phone,
            UserRole role,
            Boolean isActive,
            boolean barberOnly
    ) {
        Specification<User> spec = (root, query, cb) ->
                cb.conjunction();

        UserRole effectiveRole = barberOnly
                ? UserRole.BARBER
                : role;

        if (effectiveRole != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("role"), effectiveRole)
            );
        }

        String namePattern = PageUtils.containsIgnoreCase(fullName);

        if (namePattern != null) {
            spec = spec.and((root, query, cb) ->
                    cb.like(
                            cb.lower(root.get("fullName")),
                            namePattern
                    )
            );
        }

        String normalizedEmail =
                PageUtils.normalizeExactFilter(email);

        if (normalizedEmail != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(
                            cb.lower(root.get("email")),
                            normalizedEmail
                    )
            );
        }

        if (phone != null && !phone.isBlank()) {
            String normalizedPhone = phone.trim();

            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("phone"), normalizedPhone)
            );
        }

        if (isActive == null) {
            isActive = true;
        }

        Boolean finalIsActive = isActive;
        spec = spec.and((root, query, cb) ->
                cb.equal(root.get("isActive"), finalIsActive)
        );


        Page<User> result = userRepository.findAll(
                spec,
                PageUtils.pageable(
                        page,
                        limit,
                        Sort.by(
                                Sort.Order.asc("fullName"),
                                Sort.Order.asc("id")
                        )
                )
        );

        return PageResponse.from(result, userMapper::toResponse);
    }
}
