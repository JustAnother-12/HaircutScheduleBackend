package com.j2ee.HaircutSchedule.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "booking_services",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_booking_service",
                        columnNames = {"booking_id", "service_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(
            name = "price_at_booking",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal priceAtBooking;
}
