package com.gotogether.backend.dto;

public record RideStopResponse(
    Long stopId,
    String name,
    String city,
    Double latitude,
    Double longitude,
    Integer stopOrder
) {}
