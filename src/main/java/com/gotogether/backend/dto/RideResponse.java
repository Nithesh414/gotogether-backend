
package com.gotogether.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RideResponse(
    Long rideId,
    Long driverId,
    String driverName,
    String vehicleNumber,

    String fromLocation,
    String toLocation,

    Double fromLatitude,
    Double fromLongitude,

    Double toLatitude,
    Double toLongitude,

    LocalDateTime departureDateTime,
    Integer totalSeats,
    Integer availableSeats,
    BigDecimal pricePerSeat,
    String status,
    String driverPhotoUrl
) {}
