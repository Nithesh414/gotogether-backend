
package com.gotogether.backend.service;

import com.gotogether.backend.dto.CreateRideRequest;
import com.gotogether.backend.dto.RideResponse;
import com.gotogether.backend.entity.DriverProfile;
import com.gotogether.backend.entity.Ride;
import com.gotogether.backend.entity.User;
import com.gotogether.backend.repository.BookingRepository;
import com.gotogether.backend.repository.DriverProfileRepository;
import com.gotogether.backend.repository.RideRepository;
import com.gotogether.backend.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Service
public class RideService {

    private final RideRepository rideRepository;
    private final DriverProfileRepository driverRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    public RideService(
            RideRepository rideRepository,
            DriverProfileRepository driverRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            BookingService bookingService) {

        this.rideRepository = rideRepository;
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
    }

    // 1. CREATE RIDE

    @Transactional
    public RideResponse createRide(
            String email,
            CreateRideRequest request) {

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        DriverProfile driver = driverRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Driver profile not found"));

        if (!"APPROVED".equalsIgnoreCase(driver.getVerificationStatus())) {
            throw new IllegalArgumentException(
                    "Your driver profile is not approved");
        }

        if (request.fromLocation() == null
                || request.fromLocation().isBlank()
                || request.toLocation() == null
                || request.toLocation().isBlank()
                || request.fromLocation().trim()
                        .equalsIgnoreCase(request.toLocation().trim())) {

            throw new IllegalArgumentException(
                    "Enter valid pickup and destination locations");
        }

        if (request.departureDateTime() == null
                || !request.departureDateTime().isAfter(LocalDateTime.now())) {

            throw new IllegalArgumentException(
                    "Departure time must be in the future");
        }

        if (request.totalSeats() == null
                || request.totalSeats() < 1
                || request.totalSeats() > driver.getAvailableSeats()) {

            throw new IllegalArgumentException(
                    "Invalid seat count for this vehicle");
        }

        if (request.pricePerSeat() == null
                || request.pricePerSeat().compareTo(BigDecimal.ZERO) < 0) {

            throw new IllegalArgumentException("Invalid price per seat");
        }

        Ride ride = new Ride();

        ride.setDriver(driver);
        ride.setFromLocation(request.fromLocation().trim());
        ride.setToLocation(request.toLocation().trim());
        ride.setDepartureDateTime(request.departureDateTime());
        ride.setTotalSeats(request.totalSeats());
        ride.setAvailableSeats(request.totalSeats());
        ride.setPricePerSeat(request.pricePerSeat());
        ride.setStatus("SCHEDULED");

        return toResponse(rideRepository.save(ride));
    }

    // 2. SEARCH RIDES

    @Transactional(readOnly = true)
    public List<RideResponse> searchRides(String from, String to) {

        return rideRepository
                .findByFromLocationContainingIgnoreCaseAndToLocationContainingIgnoreCaseAndStatusIgnoreCase(
                        from, to, "SCHEDULED")
                .stream()
                .filter(ride -> ride.getAvailableSeats() > 0)
                .filter(ride -> ride.getDepartureDateTime()
                        .isAfter(LocalDateTime.now()))
                .map(this::toResponse)
                .toList();
    }

    // 3. GET DRIVER'S RIDES

    @Transactional(readOnly = true)
    public List<RideResponse> getMyRides(String email) {

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        DriverProfile driver = driverRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Driver profile not found"));

        return rideRepository
                .findByDriver_IdOrderByCreatedAtDesc(driver.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 4. FILTER DRIVER'S RIDES

    @Transactional(readOnly = true)
    public List<RideResponse> getMyRidesByStatus(
            String email,
            String status) {

        if (status == null || status.isBlank()) {
            return getMyRides(email);
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        DriverProfile driver = driverRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Driver profile not found"));

        return rideRepository
                .findByDriver_IdAndStatusIgnoreCaseOrderByCreatedAtDesc(
                        driver.getId(), status.trim())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 5. CONFIRM START RIDE AFTER ALL PASSENGERS ARE VERIFIED

    @Transactional
    public RideResponse confirmStartTrip(
            String email,
            Long rideId) {

        Ride ride = getRideForDriver(email, rideId);

        if (!"BOARDING".equalsIgnoreCase(ride.getStatus())) {
            throw new IllegalArgumentException(
                    "Start boarding before confirming the trip");
        }

        boolean passengersStillWaiting = bookingRepository
                .findByRide_IdOrderByCreatedAtDesc(rideId)
                .stream()
                .anyMatch(booking ->
                        "CONFIRMED".equalsIgnoreCase(booking.getStatus()));

        if (passengersStillWaiting) {
            throw new IllegalArgumentException(
                    "Verify all confirmed passengers before starting the ride");
        }

        ride.setStatus("IN_PROGRESS");

        return toResponse(rideRepository.save(ride));
    }

    // 6. COMPLETE TRIP AND UPDATE SIMULATED PAYMENTS

    @Transactional
    public RideResponse completeTrip(
            String email,
            Long rideId) {

        Ride ride = getRideForDriver(email, rideId);

        if (!"IN_PROGRESS".equalsIgnoreCase(ride.getStatus())) {
            throw new IllegalArgumentException(
                    "Only an in-progress ride can be completed");
        }

        ride.setStatus("COMPLETED");

        Ride completedRide = rideRepository.save(ride);

        // Update boarded passengers' simulated payments.
        // No actual payment or payout is processed.
        bookingService.markPaymentsSettlementPending(
                rideId, email);

        return toResponse(completedRide);
    }

    // 7. VERIFY DRIVER OWNERSHIP AND LOCK RIDE

    private Ride getRideForDriver(
            String email,
            Long rideId) {

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        DriverProfile driver = driverRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Driver profile not found"));

        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new IllegalArgumentException("Ride not found"));

        if (!ride.getDriver().getId().equals(driver.getId())) {
            throw new IllegalArgumentException(
                    "You are not authorized to update this ride");
        }

        return ride;
    }

    // 8. CONVERT ENTITY TO RESPONSE

    private RideResponse toResponse(Ride ride) {

        DriverProfile driver = ride.getDriver();

        return new RideResponse(
                ride.getId(),
                driver.getId(),
                driver.getUser().getFullName(),
                driver.getVehicleNumber(),
                ride.getFromLocation(),
                ride.getToLocation(),
                ride.getDepartureDateTime(),
                ride.getTotalSeats(),
                ride.getAvailableSeats(),
                ride.getPricePerSeat(),
                ride.getStatus(),
                driver.getPhotoUrl());
    }
}
