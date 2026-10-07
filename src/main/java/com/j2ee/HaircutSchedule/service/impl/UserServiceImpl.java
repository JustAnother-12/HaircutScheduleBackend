package com.j2ee.HaircutSchedule.service.impl;


import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.mapper.UserMapper;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import com.j2ee.HaircutSchedule.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public List<UserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();
        if(users.isEmpty())
            throw new ResourceNotFoundException("No users found in the system");

        return users.stream()
                .map(userMapper::toUserResponse)
                .toList();
    }
}
