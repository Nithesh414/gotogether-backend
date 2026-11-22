package com.gotogether.backend.dto;

public record VerifyPasswordResetOtpRequest(String email, String otp) {}
