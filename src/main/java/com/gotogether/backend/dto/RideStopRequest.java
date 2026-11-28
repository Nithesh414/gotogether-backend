package com.gotogether.backend.dto;

public record RideStopRequest(
    String name,
    String city,
    Double latitude,
    Double longitude
) {}
