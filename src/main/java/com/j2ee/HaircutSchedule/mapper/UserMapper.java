package com.j2ee.HaircutSchedule.mapper;

import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .build();
    }
}
