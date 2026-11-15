package com.gotogether.backend.controller;

import com.gotogether.backend.dto.BookingResponse;
import com.gotogether.backend.dto.CreateBookingRequest;
import com.gotogether.backend.service.BookingService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.gotogether.backend.dto.TripDetailsResponse;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // Passenger creates a booking.
    @PostMapping
    public BookingResponse createBooking(
        @RequestBody CreateBookingRequest request,
        Authentication authentication
    ) {
        return bookingService.createBooking(
            authentication.getName(),
            request
        );
    }

    // View all bookings made by the logged-in passenger.
    @GetMapping("/my")
    public List<BookingResponse> getMyBookings(
        Authentication authentication
    ) {
        return bookingService.getMyBookings(authentication.getName());
    }

    // View one booking belonging to the logged-in passenger.
    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(
        @PathVariable Long bookingId,
        Authentication authentication
    ) {
        return bookingService.getBooking(
            bookingId,
            authentication.getName()
        );
    }


@GetMapping("/{bookingId}/details")
public TripDetailsResponse getTripDetails(
    @PathVariable Long bookingId,
    Authentication authentication
) {
    return bookingService.getTripDetails(
        bookingId,
        authentication.getName()
    );
}







    @GetMapping("/my/filter")
public List<BookingResponse> getMyBookingsByStatus(
    @RequestParam(required = false) String status,
    Authentication authentication
) {
    return bookingService.getMyBookingsByStatus(
        authentication.getName(),
        status
    );
}

    // Cancel a booking belonging to the logged-in passenger.
    @PatchMapping("/{bookingId}/cancel")
    public BookingResponse cancelBooking(
        @PathVariable Long bookingId,
        Authentication authentication
    ) {
        return bookingService.cancelBooking(
            bookingId,
            authentication.getName()
        );
    }
}