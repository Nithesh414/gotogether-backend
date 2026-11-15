package com.gotogether.backend.dto;

public record AdminLoginRequest(
    String email,
    String password
) {}