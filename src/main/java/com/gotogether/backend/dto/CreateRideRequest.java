package com.gotogether.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateRideRequest(
    String fromLocation,
    String toLocation,

    Double fromLatitude,
    Double fromLongitude,

    Double toLatitude,
    Double toLongitude,

    LocalDateTime departureDateTime,
    Integer totalSeats,
    BigDecimal pricePerSeat,

    List<RideStopRequest> stops
) {}