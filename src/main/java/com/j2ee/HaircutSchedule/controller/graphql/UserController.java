package com.j2ee.HaircutSchedule.controller.graphql;

import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.j2ee.HaircutSchedule.util.helpers.parseId;

@Controller
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserController {
    private final UserService userService;

    @PreAuthorize("hasAuthority('ADMIN')")
    @QueryMapping
    public List<UserResponse> users() {
        return userService.getAllUsers();
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @QueryMapping
    public UserResponse user(@Argument String id) {
        Integer userId = parseId(id);
        return userService.getUserById(userId);
    }
}
