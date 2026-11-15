
package com.gotogether.backend.dto;

import java.time.LocalDateTime;

public record AdminDriverResponse(
        Long driverId,
        Long userId,
        String fullName,
        String email,
        String mobileNumber,
        String vehicleNumber,
        String vehicleType,
        String vehicleModel,
        Integer availableSeats,
        String verificationStatus,
        String rejectionReason,
        LocalDateTime createdAt
) {
}
