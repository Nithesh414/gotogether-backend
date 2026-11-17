
package com.gotogether.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(
        Long bookingId,
        Long rideId,
        Long passengerId,
        String passengerName,

        String fromLocation,
        String toLocation,

        // Pickup and destination coordinates
        Double fromLatitude,
        Double fromLongitude,
        Double toLatitude,
        Double toLongitude,

        LocalDateTime departureDateTime,
        Integer seatsBooked,
        BigDecimal totalAmount,
        String bookingStatus,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        String paymentStatus,
        String paymentReference,
        LocalDateTime paidAt
) {}
