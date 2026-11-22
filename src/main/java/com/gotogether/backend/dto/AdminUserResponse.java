package com.gotogether.backend.dto;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long userId,
        String fullName,
        String email,
        String mobileNumber,
        String role,
        LocalDateTime createdAt,
        Long driverId,
        String driverVerificationStatus
) {}
