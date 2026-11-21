package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.AuthResponse;
import com.j2ee.HaircutSchedule.entity.Session;
import com.j2ee.HaircutSchedule.entity.User;

public interface AuthService {
    AuthResponse register(
            String fullName,
            String email,
            String phone,
            String password
    );
    AuthResponse login(
            String email,
            String password
    );
    AuthResponse loginWithUser(User user);
    void logout(String refreshToken);
    AuthResponse refreshToken(String refreshToken);
    Session createSession(User user, String tokenHash);
}
