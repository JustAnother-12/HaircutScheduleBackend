package com.j2ee.HaircutSchedule.mapper;

import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.entity.User;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
public class UserMapper {
    public UserResponse toResponse(User user) {

        if (user == null) {
            return null;
        }

        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .isActive(user.isActive())
                .createdAt(
                        user.getCreatedAt() == null
                                ? null
                                : user.getCreatedAt()
                                .atOffset(ZoneOffset.UTC)
                )
                .build();
    }
}
