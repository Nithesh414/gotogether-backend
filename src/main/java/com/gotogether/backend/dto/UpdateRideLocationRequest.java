package com.gotogether.backend.dto;

public record UpdateRideLocationRequest(
    Double latitude,
    Double longitude,
    Double accuracyMeters,
    Double speedMetersPerSecond,
    Double headingDegrees
) {}