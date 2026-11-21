package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.AuthResponse;
import com.j2ee.HaircutSchedule.dto.response.MessageResponse;
import com.j2ee.HaircutSchedule.dto.response.RegistrationResult;
import com.j2ee.HaircutSchedule.dto.response.SessionResponse;
import com.j2ee.HaircutSchedule.entity.Session;
import com.j2ee.HaircutSchedule.entity.User;

import java.util.List;

public interface AuthService {
    RegistrationResult register(
            String fullName,
            String email,
            String phone,
            String password
    );

    AuthResponse confirmRegistration(String token);

    AuthResponse login(
            String email,
            String password
    );

    AuthResponse loginWithUser(User user);

    MessageResponse requestPasswordReset(String email);

    Boolean resetPassword(
            String token,
            String newPassword
    );

    void logout(String refreshToken);

    AuthResponse refreshToken(String refreshToken);

    Session createSession(User user, String refreshToken);

    List<SessionResponse> getActiveDevices(User currentUser);

    void logoutAll(User currentUser);
}
