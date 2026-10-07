package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.UserResponse;

import java.util.List;

public interface UserService {
    List<UserResponse> getAllUsers();
}
