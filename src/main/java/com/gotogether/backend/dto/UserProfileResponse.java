package com.gotogether.backend.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(
    Long userId,
    String fullName,
    String mobileNumber,
    String email,
    String role,
    LocalDateTime createdAt
) {}