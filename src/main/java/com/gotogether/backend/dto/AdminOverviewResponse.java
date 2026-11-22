package com.gotogether.backend.dto;

import java.math.BigDecimal;

public record AdminOverviewResponse(
        long totalUsers,
        long totalDrivers,
        long pendingDrivers,
        long approvedDrivers,
        long rejectedDrivers,
        long totalRides,
        long activeRides,
        long completedRides,
        long cancelledRides,
        long totalBookings,
        long confirmedBookings,
        long cancelledBookings,
        BigDecimal simulatedBookingValue
) {}
