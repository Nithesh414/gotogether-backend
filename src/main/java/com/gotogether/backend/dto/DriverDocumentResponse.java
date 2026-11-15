package com.gotogether.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DriverDocumentResponse(
    Long documentId,
    String documentType,
    String documentSide,
    String fileFormat,
    String contentType,
    Long fileSize,
    LocalDate expiryDate,
    String reviewStatus,
    String rejectionReason,
    LocalDateTime uploadedAt,
    LocalDateTime reviewedAt
) {}