package com.j2ee.HaircutSchedule.controller;

import com.j2ee.HaircutSchedule.dto.response.ApiResponse;
import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();

        ApiResponse<List<UserResponse>> response = ApiResponse.<List<UserResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Get users successfully!")
                .data(users)
                .build();

        return ResponseEntity.ok(response);
    }
}
