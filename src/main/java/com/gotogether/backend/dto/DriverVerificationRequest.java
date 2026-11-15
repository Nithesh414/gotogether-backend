
package com.gotogether.backend.dto;

public record DriverVerificationRequest(
        String status,
        String rejectionReason
) {
}
