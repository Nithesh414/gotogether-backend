package com.gotogether.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TripDetailsResponse(
    Long rideId,
    Long bookingId,
    String fromLocation,
    String toLocation,
    LocalDateTime departureDateTime,
    String rideStatus,
    String bookingStatus,
    Long driverId,
    String driverName,
    String vehicleNumber,
    String vehicleType,
    String vehicleModel,
    Long passengerId,
    String passengerName,
    Integer seatsBooked,
    Integer totalSeats,
    Integer availableSeats,
    BigDecimal pricePerSeat,
    BigDecimal totalAmount,
    LocalDateTime bookingCreatedAt,
    LocalDateTime cancelledAt
) {}