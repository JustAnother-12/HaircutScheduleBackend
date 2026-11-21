package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.UserResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import org.springframework.graphql.data.ArgumentValue;

import java.util.List;

public interface UserService {
    UserResponse updateCurrentUserProfile(
            User currentUser,
            ArgumentValue<String> fullName,
            ArgumentValue<String> email,
            ArgumentValue<String> phone
    );

    Boolean updateCurrentUserPassword(
            User currentUser,
            ArgumentValue<String> oldPassword,
            ArgumentValue<String> password
    );

    UserResponse getCurrentUser(User currentUser);

    List<UserResponse> getAllUsers();

    List<UserResponse> getAllBarbers();

    UserResponse getUserById(Integer id);

    UserResponse createUser(
            String fullName,
            String email,
            String phone,
            String password,
            UserRole role
    );

    UserResponse updateUser(
            Integer userId,
            ArgumentValue<String> fullName,
            ArgumentValue<String> email,
            ArgumentValue<String> phone,
            ArgumentValue<UserRole> role
    );
}
