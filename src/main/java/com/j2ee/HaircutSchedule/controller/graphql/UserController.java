package com.j2ee.HaircutSchedule.controller.graphql;

import com.j2ee.HaircutSchedule.dto.request.UpdatePasswordInput;
import com.j2ee.HaircutSchedule.dto.request.UpdateProfileInput;
import com.j2ee.HaircutSchedule.dto.request.UpdateUserInput;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.security.CustomUserDetails;
import com.j2ee.HaircutSchedule.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.j2ee.HaircutSchedule.util.Helpers.parseId;

@Controller
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserController {
    private final UserService userService;

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return userService.getCurrentUser(principal.getUser());
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public PageResponse<UserResponse> users(
            @Argument Integer page,
            @Argument Integer limit,
            @Argument String fullName,
            @Argument String email,
            @Argument String phone,
            @Argument UserRole role
    ) {
        return userService.getUsers(
                page,
                limit,
                fullName,
                email,
                phone,
                role
        );
    }

    @QueryMapping
    public PageResponse<UserResponse> barbers(
            @Argument Integer page,
            @Argument Integer limit,
            @Argument String fullName,
            @Argument String email,
            @Argument String phone,
            @Argument Boolean isActive
    ) {
        return userService.getBarbers(
                page,
                limit,
                fullName,
                email,
                phone,
                isActive
        );
    }

    @QueryMapping
    public UserResponse barber(@Argument String id) {
        return userService.getBarberById(parseId(id));
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public UserResponse user(@Argument String id) {
        Integer userId = parseId(id);
        return userService.getUserById(userId);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public UserResponse updateMyProfile(
            @Argument UpdateProfileInput input,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return userService.updateCurrentUserProfile(
                principal.getUser(),
                input.fullName(),
                input.email(),
                input.phone()
        );
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Boolean updateMyPassword(
            @Argument UpdatePasswordInput input,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return userService.updateCurrentUserPassword(
                principal.getUser(),
                input.oldPassword(),
                input.newPassword()
        );
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public UserResponse createUser(
            @Argument String fullName,
            @Argument String email,
            @Argument String phone,
            @Argument String password,
            @Argument UserRole role
    ) {
        return userService.createUser(
                fullName,
                email,
                phone,
                password,
                role
        );
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public UserResponse updateUser(
            @Argument String id,
            @Argument UpdateUserInput input
    ) {
        return userService.updateUser(
                parseId(id),
                input.fullName(),
                input.email(),
                input.phone(),
                input.role()
        );
    }
}
