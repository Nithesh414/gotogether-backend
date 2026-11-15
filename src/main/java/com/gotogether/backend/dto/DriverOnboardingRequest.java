package com.gotogether.backend.dto;

public record DriverOnboardingRequest(
    String vehicleNumber,
    String vehicleType,
    String vehicleModel,
    String licenseNumber,
    Integer availableSeats
) {}