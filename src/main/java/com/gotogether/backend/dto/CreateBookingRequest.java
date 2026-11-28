package com.gotogether.backend.dto;

public record CreateBookingRequest(
    Long rideId,
    Integer seatsBooked,
    String fromLocation,
    String toLocation
) {
}