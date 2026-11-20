package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.AuthResponse;
import com.j2ee.HaircutSchedule.entity.Session;
import com.j2ee.HaircutSchedule.entity.User;

public interface AuthService {
    public AuthResponse login(
            String email,
            String password
    );
    public AuthResponse loginWithUser(User user);
    public void logout(String refreshToken);
    public AuthResponse refreshToken(String refreshToken);
    public Session createSession(User user, String tokenHash);
}
