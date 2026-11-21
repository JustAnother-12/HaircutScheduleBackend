package com.j2ee.HaircutSchedule.controller.graphql;

import com.j2ee.HaircutSchedule.dto.request.RegisterInput;
import com.j2ee.HaircutSchedule.dto.response.AuthResponse;
import com.j2ee.HaircutSchedule.dto.response.MessageResponse;
import com.j2ee.HaircutSchedule.dto.response.RegistrationResult;
import com.j2ee.HaircutSchedule.service.AuthService;
import com.j2ee.HaircutSchedule.service.OAuthLoginTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final OAuthLoginTicketService ticketService;

    @MutationMapping
    public RegistrationResult register(
            @Argument RegisterInput input
    ) {
        return authService.register(
                input.fullName(),
                input.email(),
                input.phone(),
                input.password()
        );
    }

    @MutationMapping
    public AuthResponse confirmRegistration(
            @Argument String token
    ) {
        return authService.confirmRegistration(token);
    }

    @MutationMapping
    public MessageResponse requestPasswordReset(
            @Argument String email
    ) {
        return authService.requestPasswordReset(email);
    }

    @MutationMapping
    public Boolean resetPassword(
            @Argument String token,
            @Argument String newPassword
    ) {
        return authService.resetPassword(
                token,
                newPassword
        );
    }

    @MutationMapping
    public AuthResponse login(
            @Argument String email,
            @Argument String password
    ){
        return authService.login(email, password);
    }

    @MutationMapping
    public AuthResponse refreshToken(
            @Argument String refreshToken
    ){
        return authService.refreshToken(refreshToken);
    }

    @MutationMapping
    public Boolean logout(
            @Argument String refreshToken
    ){
        authService.logout(refreshToken);
        return true;
    }

    @MutationMapping
    public AuthResponse exchangeSocialLogin(
            @Argument String code
    ){
        return ticketService.consume(code);
    }
}
