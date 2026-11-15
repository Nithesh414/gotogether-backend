package com.gotogether.backend.dto;

public record UpdateProfileRequest(
    String fullName,
    String mobileNumber,
    String email
) {}