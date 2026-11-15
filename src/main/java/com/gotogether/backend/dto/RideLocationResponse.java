package com.gotogether.backend.dto;

import java.time.LocalDateTime;

public record RideLocationResponse(
    Long rideId,
    Double latitude,
    Double longitude,
    Double accuracyMeters,
    Double speedMetersPerSecond,
    Double headingDegrees,
    LocalDateTime updatedAt
) {}