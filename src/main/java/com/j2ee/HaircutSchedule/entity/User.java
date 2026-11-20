package com.j2ee.HaircutSchedule.entity;

import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.enums.UserRoleConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "hashed_password", length = 255)
    private String hashedPassword;

    @Convert(converter = UserRoleConverter.class)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.CUSTOMER;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
