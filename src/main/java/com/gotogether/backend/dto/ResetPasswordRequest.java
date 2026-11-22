package com.gotogether.backend.dto;

public record ResetPasswordRequest(String email, String resetToken, String newPassword) {}
