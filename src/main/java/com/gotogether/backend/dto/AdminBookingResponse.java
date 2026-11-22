package com.gotogether.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminBookingResponse(
        Long bookingId,
        Long rideId,
        String fromLocation,
        String toLocation,
        LocalDateTime departureDateTime,
        Long passengerId,
        String passengerName,
        String passengerEmail,
        String passengerMobile,
        String driverName,
        Integer seatsBooked,
        BigDecimal totalAmount,
        String bookingStatus,
        String paymentStatus,
        String paymentReference,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt
) {}
