package com.gotogether.backend.dto;

import java.time.LocalDateTime;

public record DriverProfileResponse(
    Long driverId,
    String vehicleNumber,
    String vehicleType,
    String vehicleModel,
    Integer availableSeats,
    String verificationStatus,
    LocalDateTime createdAt,
    String photoUrl
) {}