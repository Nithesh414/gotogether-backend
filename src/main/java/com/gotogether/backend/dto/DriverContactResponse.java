package com.gotogether.backend.dto;

public record DriverContactResponse(
    Long driverId,
    String driverName,
    String phoneNumber,
    String email
) {}