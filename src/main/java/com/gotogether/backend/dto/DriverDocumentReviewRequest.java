package com.gotogether.backend.dto;

public record DriverDocumentReviewRequest(
        String status,
        String rejectionReason
) {
}