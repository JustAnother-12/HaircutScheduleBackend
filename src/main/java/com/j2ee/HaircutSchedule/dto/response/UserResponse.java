package com.j2ee.HaircutSchedule.dto.response;

import com.j2ee.HaircutSchedule.enums.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private UserRole role;
}
