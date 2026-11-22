package com.gotogether.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminRideResponse(
        Long rideId,
        Long driverId,
        String driverName,
        String driverEmail,
        String vehicleNumber,
        String fromLocation,
        String toLocation,
        LocalDateTime departureDateTime,
        Integer totalSeats,
        Integer availableSeats,
        BigDecimal pricePerSeat,
        String status,
        LocalDateTime createdAt,
        boolean gpsSharingActive
) {}
