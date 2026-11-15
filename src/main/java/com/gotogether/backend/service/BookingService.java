
package com.gotogether.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.gotogether.backend.dto.BookingResponse;
import com.gotogether.backend.dto.CreateBookingRequest;
import com.gotogether.backend.dto.DriverContactResponse;
import com.gotogether.backend.dto.TripDetailsResponse;
import com.gotogether.backend.entity.Booking;
import com.gotogether.backend.entity.Ride;
import com.gotogether.backend.entity.User;
import com.gotogether.backend.repository.BookingRepository;
import com.gotogether.backend.repository.RideRepository;
import com.gotogether.backend.repository.UserRepository;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final EmailJsService emailJsService;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    public BookingService(
            BookingRepository bookingRepository,
            RideRepository rideRepository,
            UserRepository userRepository,
            EmailJsService emailJsService,
            PasswordEncoder passwordEncoder) {

        this.bookingRepository = bookingRepository;
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.emailJsService = emailJsService;
        this.passwordEncoder = passwordEncoder;
    }

    // 1. CREATE BOOKING WITH SIMULATED PAYMENT

    @Transactional
    public BookingResponse createBooking(
            String passengerEmail,
            CreateBookingRequest request) {

        if (request == null || request.rideId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ride ID is required");
        }

        if (request.seatsBooked() == null || request.seatsBooked() < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "At least one seat must be booked");
        }

        User passenger = userRepository
                .findByEmailIgnoreCase(passengerEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Passenger account not found"));

        // Lock ride to prevent overbooking.
        Ride ride = rideRepository.findByIdForUpdate(request.rideId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ride not found"));

        if (ride.getDriver().getUser().getId().equals(passenger.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "You cannot book your own ride");
        }

        if (!"SCHEDULED".equalsIgnoreCase(ride.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "This ride is not available for booking");
        }

        if (!ride.getDepartureDateTime().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "This ride has already departed");
        }

        if (request.seatsBooked() > ride.getAvailableSeats()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Not enough seats are available");
        }

        boolean alreadyBooked = bookingRepository
                .existsByRide_IdAndPassenger_IdAndStatusIgnoreCase(
                        ride.getId(), passenger.getId(), "CONFIRMED");

        if (alreadyBooked) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have a confirmed booking for this ride");
        }

        BigDecimal totalAmount = ride.getPricePerSeat()
                .multiply(BigDecimal.valueOf(request.seatsBooked()))
                .setScale(2, RoundingMode.HALF_UP);

        Booking booking = new Booking();

        booking.setRide(ride);
        booking.setPassenger(passenger);
        booking.setSeatsBooked(request.seatsBooked());
        booking.setTotalAmount(totalAmount);
        booking.setStatus("CONFIRMED");

        // Simulated payment only. No actual payment gateway is connected.
        booking.setPaymentStatus("SIMULATED_PAID");
        booking.setPaymentReference("SIM-" + UUID.randomUUID());
        booking.setPaidAt(LocalDateTime.now());

        ride.setAvailableSeats(
                ride.getAvailableSeats() - request.seatsBooked());

        rideRepository.save(ride);

        // OTP is generated later when the driver starts boarding.
        Booking savedBooking = bookingRepository.save(booking);

        return toResponse(savedBooking);
    }

    // 2. START BOARDING AND SEND INDIVIDUAL OTPs

    @Transactional
    public Map<String, Object> startBoarding(
            Long rideId,
            String driverEmail) {

        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ride not found"));

        verifyDriverOwnership(ride, driverEmail);

        if (!"SCHEDULED".equalsIgnoreCase(ride.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only scheduled rides can begin boarding");
        }

        List<Booking> bookings =
                bookingRepository.findByRide_IdOrderByCreatedAtDesc(rideId);

        List<Booking> confirmedBookings = bookings.stream()
                .filter(b -> "CONFIRMED".equalsIgnoreCase(b.getStatus()))
                .toList();

        // Start the boarding stage.
        ride.setStatus("BOARDING");
        rideRepository.save(ride);

        int emailsSent = 0;

        for (Booking booking : confirmedBookings) {

            String otp = generateOtp();

            booking.setBoardingOtpHash(passwordEncoder.encode(otp));
            booking.setBoardingOtpExpiresAt(
                    LocalDateTime.now().plusMinutes(5));
            booking.setBoardingOtpAttempts(0);

            bookingRepository.save(booking);

            // Send a unique OTP to each passenger.
            emailJsService.sendOtp(
                    booking.getPassenger().getEmail(),
                    booking.getPassenger().getFullName(),
                    otp);

            emailsSent++;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Boarding started. OTPs sent to passengers.");
        response.put("rideId", rideId);
        response.put("rideStatus", "BOARDING");
        response.put("passengersNotified", emailsSent);

        return response;
    }

    // 3. RESEND BOARDING OTP

    @Transactional
    public Map<String, Object> resendBoardingOtp(
            Long rideId,
            Long bookingId,
            String driverEmail) {

        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ride not found"));

        verifyDriverOwnership(ride, driverEmail);

        if (!"BOARDING".equalsIgnoreCase(ride.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Boarding has not started");
        }

        Long actualRideId = bookingRepository
                .findRideIdByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        if (!actualRideId.equals(rideId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Booking not found");
        }

        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        if (!"CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This passenger is not awaiting boarding verification");
        }

        if (booking.getBoardingOtpExpiresAt() != null
                && booking.getBoardingOtpExpiresAt().isAfter(LocalDateTime.now())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The current OTP is still active");
        }

        String otp = generateOtp();

        booking.setBoardingOtpHash(passwordEncoder.encode(otp));
        booking.setBoardingOtpExpiresAt(
                LocalDateTime.now().plusMinutes(5));
        booking.setBoardingOtpAttempts(0);

        bookingRepository.save(booking);

        emailJsService.sendOtp(
                booking.getPassenger().getEmail(),
                booking.getPassenger().getFullName(),
                otp);

        return Map.of(
                "success", true,
                "message", "A new boarding OTP has been emailed",
                "bookingId", bookingId);
    }

    // 4. VERIFY PASSENGER BOARDING OTP

    @Transactional
    public Map<String, Object> verifyBoardingOtp(
            Long rideId,
            Long bookingId,
            String driverEmail,
            String otp) {

        if (otp == null || !otp.matches("^[0-9]{6}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Enter a valid 6-digit OTP");
        }

        Long actualRideId = bookingRepository
                .findRideIdByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        if (!actualRideId.equals(rideId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Booking not found");
        }

        // Always lock ride before booking.
        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ride not found"));

        verifyDriverOwnership(ride, driverEmail);

        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        if (!"BOARDING".equalsIgnoreCase(ride.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This ride is not accepting boarding verification");
        }

        if (!"CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This booking is not eligible for boarding");
        }

        if (booking.getBoardingOtpHash() == null
                || booking.getBoardingOtpExpiresAt() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No active OTP. Request a new OTP.");
        }

        if (!booking.getBoardingOtpExpiresAt().isAfter(LocalDateTime.now())) {

            booking.setBoardingOtpHash(null);
            booking.setBoardingOtpExpiresAt(null);

            bookingRepository.save(booking);

            return Map.of(
                    "success", false,
                    "message", "OTP expired. Request a new OTP.");
        }

        int attempts = booking.getBoardingOtpAttempts() == null
                ? 0
                : booking.getBoardingOtpAttempts();

        if (attempts >= 5) {
            return Map.of(
                    "success", false,
                    "message", "Maximum OTP attempts reached. Request a new OTP.");
        }

        if (!passwordEncoder.matches(otp, booking.getBoardingOtpHash())) {

            attempts++;
            booking.setBoardingOtpAttempts(attempts);
            bookingRepository.save(booking);

            if (attempts >= 5) {
                return Map.of(
                        "success", false,
                        "message", "Maximum OTP attempts reached. Request a new OTP.");
            }

            return Map.of(
                    "success", false,
                    "message", "Incorrect OTP",
                    "attemptsRemaining", 5 - attempts);
        }

        // OTP verified successfully.
        booking.setStatus("BOARDED");
        booking.setBoardedAt(LocalDateTime.now());

        // Clear OTP so it cannot be reused.
        booking.setBoardingOtpHash(null);
        booking.setBoardingOtpExpiresAt(null);

        bookingRepository.save(booking);

        return Map.of(
                "success", true,
                "message", "Passenger boarding verified successfully",
                "bookingId", booking.getId(),
                "bookingStatus", "BOARDED",
                "boardedAt", booking.getBoardedAt().toString());
    }

    // 5. MARK SIMULATED PAYMENTS AS SETTLEMENT PENDING

    @Transactional
    public int markPaymentsSettlementPending(
            Long rideId,
            String driverEmail) {

        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ride not found"));

        verifyDriverOwnership(ride, driverEmail);

        if (!"COMPLETED".equalsIgnoreCase(ride.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Complete the ride before settlement");
        }

        List<Booking> bookings =
                bookingRepository.findByRide_IdOrderByCreatedAtDesc(rideId);

        int updated = 0;

        for (Booking booking : bookings) {

            if ("BOARDED".equalsIgnoreCase(booking.getStatus())
                    && "SIMULATED_PAID".equalsIgnoreCase(
                            booking.getPaymentStatus())) {

                booking.setPaymentStatus("SETTLEMENT_PENDING");
                bookingRepository.save(booking);
                updated++;
            }
        }

        return updated;
    }

    // 6. PASSENGER BOOKING HISTORY

    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(String passengerEmail) {

        return bookingRepository
                .findByPassenger_EmailIgnoreCaseOrderByCreatedAtDesc(
                        passengerEmail)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(
            Long bookingId,
            String passengerEmail) {

        Booking booking = bookingRepository
                .findByIdAndPassenger_EmailIgnoreCase(
                        bookingId, passengerEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        return toResponse(booking);
    }

    // 7. PASSENGER TRIP DETAILS

    @Transactional(readOnly = true)
    public TripDetailsResponse getTripDetails(
            Long bookingId,
            String passengerEmail) {

        Booking booking = bookingRepository
                .findByIdAndPassenger_EmailIgnoreCase(
                        bookingId, passengerEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        Ride ride = booking.getRide();
        User passenger = booking.getPassenger();
        var driver = ride.getDriver();

        return new TripDetailsResponse(
                ride.getId(),
                booking.getId(),
                ride.getFromLocation(),
                ride.getToLocation(),
                ride.getDepartureDateTime(),
                ride.getStatus(),
                booking.getStatus(),
                driver.getId(),
                driver.getUser().getFullName(),
                driver.getVehicleNumber(),
                driver.getVehicleType(),
                driver.getVehicleModel(),
                passenger.getId(),
                passenger.getFullName(),
                booking.getSeatsBooked(),
                ride.getTotalSeats(),
                ride.getAvailableSeats(),
                ride.getPricePerSeat(),
                booking.getTotalAmount(),
                booking.getCreatedAt(),
                booking.getCancelledAt());
    }

    // 8. DRIVER PASSENGER LIST

    @Transactional(readOnly = true)
    public List<BookingResponse> getPassengersForDriver(
            Long rideId,
            String driverEmail) {

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ride not found"));

        verifyDriverOwnership(ride, driverEmail);

        return bookingRepository
                .findByRide_IdOrderByCreatedAtDesc(rideId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 9. FILTER PASSENGER BOOKINGS

    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookingsByStatus(
            String email,
            String status) {

        if (status == null || status.isBlank()) {
            return getMyBookings(email);
        }

        return bookingRepository
                .findByPassenger_EmailIgnoreCaseAndStatusIgnoreCaseOrderByCreatedAtDesc(
                        email, status.trim())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 10. CANCEL BOOKING


    // 10. CANCEL BOOKING - 5 HOUR CANCELLATION POLICY

    @Transactional
    public BookingResponse cancelBooking(
            Long bookingId,
            String passengerEmail) {

        Long rideId = bookingRepository
                .findRideIdForPassenger(bookingId, passengerEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        // Lock ride first to prevent concurrent seat updates.
        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ride not found"));

        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        // Verify passenger ownership.
        if (!booking.getPassenger().getEmail()
                .equalsIgnoreCase(passengerEmail)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Booking not found");
        }

        // Only confirmed bookings can be cancelled.
        if (!"CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only confirmed bookings can be cancelled");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime departureTime = ride.getDepartureDateTime();

        // Reject cancellation after departure.
        if (!departureTime.isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Booking cannot be cancelled after departure");
        }

        // Cancellation deadline: exactly 5 hours before departure.
        LocalDateTime cancellationDeadline =
                departureTime.minusHours(5);

        if (now.isAfter(cancellationDeadline)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cancellation is allowed only up to 5 hours before departure");
        }

        // Restore available seats.
        ride.setAvailableSeats(
                ride.getAvailableSeats() + booking.getSeatsBooked());

        // Update booking status.
        booking.setStatus("CANCELLED");
        booking.setCancelledAt(now);

        // Clear boarding OTP.
        booking.setBoardingOtpHash(null);
        booking.setBoardingOtpExpiresAt(null);
        booking.setBoardingOtpAttempts(0);

        // Simulated payment only; no actual refund is processed.
        rideRepository.save(ride);

        Booking savedBooking = bookingRepository.save(booking);

        return toResponse(savedBooking);
    }


    // 11. DRIVER CONTACT

    @Transactional(readOnly = true)
    public DriverContactResponse getDriverContact(
            Long rideId,
            String passengerEmail) {

        boolean hasConfirmedBooking = bookingRepository
                .existsByRide_IdAndPassenger_EmailIgnoreCaseAndStatusIgnoreCase(
                        rideId, passengerEmail, "CONFIRMED");

        if (!hasConfirmedBooking) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You need an active confirmed booking to view driver contact");
        }

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Ride not found"));

        if ("COMPLETED".equalsIgnoreCase(ride.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Driver contact is unavailable for completed rides");
        }

        User driverUser = ride.getDriver().getUser();

        return new DriverContactResponse(
                ride.getDriver().getId(),
                driverUser.getFullName(),
                driverUser.getMobileNumber(),
                driverUser.getEmail());
    }

    // 12. COMMON HELPER METHODS

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1000000));
    }

    private void verifyDriverOwnership(
            Ride ride,
            String driverEmail) {

        if (!ride.getDriver().getUser().getEmail()
                .equalsIgnoreCase(driverEmail)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Ride not found");
        }
    }

    private BookingResponse toResponse(Booking booking) {

        Ride ride = booking.getRide();
        User passenger = booking.getPassenger();

        return new BookingResponse(
                booking.getId(),
                ride.getId(),
                passenger.getId(),
                passenger.getFullName(),
                ride.getFromLocation(),
                ride.getToLocation(),
                ride.getDepartureDateTime(),
                booking.getSeatsBooked(),
                booking.getTotalAmount(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getCancelledAt(),
                booking.getPaymentStatus(),
                booking.getPaymentReference(),
                booking.getPaidAt());
    }
}
