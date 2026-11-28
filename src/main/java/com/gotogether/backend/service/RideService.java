
package com.gotogether.backend.service;

import com.gotogether.backend.dto.CreateRideRequest;
import com.gotogether.backend.dto.RideResponse;
import com.gotogether.backend.dto.RideStopRequest;
import com.gotogether.backend.dto.RideStopResponse;
import com.gotogether.backend.entity.DriverProfile;
import com.gotogether.backend.entity.Ride;
import com.gotogether.backend.entity.RideStop;
import com.gotogether.backend.entity.User;
import com.gotogether.backend.repository.BookingRepository;
import com.gotogether.backend.repository.DriverProfileRepository;
import com.gotogether.backend.repository.RideRepository;
import com.gotogether.backend.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class RideService {

    private final RideRepository rideRepository;
    private final DriverProfileRepository driverRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final SegmentAvailabilityService segmentAvailabilityService;
    private final MapboxRouteDistanceService mapboxRouteDistanceService;

    public RideService(
            RideRepository rideRepository,
            DriverProfileRepository driverRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            BookingService bookingService,
            SegmentAvailabilityService segmentAvailabilityService,
            MapboxRouteDistanceService mapboxRouteDistanceService) {

        this.rideRepository = rideRepository;
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.segmentAvailabilityService = segmentAvailabilityService;
        this.mapboxRouteDistanceService = mapboxRouteDistanceService;
    }

    // 1. CREATE RIDE

    @Transactional
    public RideResponse createRide(
            String email,
            CreateRideRequest request) {

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        DriverProfile driver = driverRepository.findByUser_Id(user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Driver profile not found"));

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

        // Validate pickup and destination coordinates.
        if (!isValidLatitude(request.fromLatitude())
                || !isValidLongitude(request.fromLongitude())
                || !isValidLatitude(request.toLatitude())
                || !isValidLongitude(request.toLongitude())) {

            throw new IllegalArgumentException(
                    "Select valid pickup and destination locations");
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

        ride.setFromLatitude(request.fromLatitude());
        ride.setFromLongitude(request.fromLongitude());
        ride.setToLatitude(request.toLatitude());
        ride.setToLongitude(request.toLongitude());

        ride.setDepartureDateTime(request.departureDateTime());
        ride.setTotalSeats(request.totalSeats());
        ride.setAvailableSeats(request.totalSeats());
        ride.setPricePerSeat(request.pricePerSeat());
        ride.setStatus("SCHEDULED");

        // 2. VALIDATE AND ATTACH INTERMEDIATE STOPS

        List<RideStopRequest> requestedStops =
                request.stops() == null ? List.of() : request.stops();

        if (requestedStops.size() > 5) {
            throw new IllegalArgumentException(
                    "You can select a maximum of 5 intermediate stops");
        }

        List<RideStop> rideStops = new ArrayList<>();

        for (int i = 0; i < requestedStops.size(); i++) {

            RideStopRequest stopRequest = requestedStops.get(i);

            if (stopRequest == null
                    || stopRequest.name() == null
                    || stopRequest.name().isBlank()
                    || stopRequest.city() == null
                    || stopRequest.city().isBlank()) {

                throw new IllegalArgumentException(
                        "Every intermediate stop must have a valid name and city");
            }

            if (!isValidLatitude(stopRequest.latitude())
                    || !isValidLongitude(stopRequest.longitude())) {

                throw new IllegalArgumentException(
                        "Select a valid location for every intermediate stop");
            }

            String city = stopRequest.city().trim();

            if (city.equalsIgnoreCase(request.fromLocation().trim())
                    || city.equalsIgnoreCase(request.toLocation().trim())) {

                throw new IllegalArgumentException(
                        "Intermediate stops cannot be the starting point or destination");
            }

            boolean duplicate = rideStops.stream()
                    .anyMatch(existing ->
                            existing.getCity().equalsIgnoreCase(city));

            if (duplicate) {
                throw new IllegalArgumentException(
                        "Duplicate intermediate stops are not allowed");
            }

            RideStop stop = new RideStop();

            stop.setRide(ride);
            stop.setName(stopRequest.name().trim());
            stop.setCity(city);
            stop.setLatitude(stopRequest.latitude());
            stop.setLongitude(stopRequest.longitude());
            stop.setStopOrder(i + 1);

            rideStops.add(stop);
        }

        ride.setStops(rideStops);

        return toResponse(rideRepository.save(ride));
    }

    // 3. COORDINATE VALIDATION

    private boolean isValidLatitude(Double latitude) {
        return latitude != null
                && Double.isFinite(latitude)
                && latitude >= -90
                && latitude <= 90;
    }

    private boolean isValidLongitude(Double longitude) {
        return longitude != null
                && Double.isFinite(longitude)
                && longitude >= -180
                && longitude <= 180;
    }

    // 4. SEARCH RIDES

    @Transactional(readOnly = true)
    public List<RideResponse> searchRides(String from, String to) {

        if (from == null || from.isBlank()
                || to == null || to.isBlank()) {
            return List.of();
        }

        List<RideResponse> results = new ArrayList<>();

        for (Ride ride : rideRepository.findByStatusIgnoreCase("SCHEDULED")) {

            if (ride.getDepartureDateTime() == null
                    || !ride.getDepartureDateTime().isAfter(LocalDateTime.now())) {
                continue;
            }

            SegmentAvailabilityService.Segment segment;

            try {
                segment = segmentAvailabilityService.resolve(ride, from, to);
            } catch (org.springframework.web.server.ResponseStatusException ignored) {
                // Skip only invalid or backward journey segments.
                continue;
            }

            int seats = segmentAvailabilityService.availableSeats(ride, segment);

            if (seats > 0) {
                // Keep Mapbox errors outside the route-validation catch.
                results.add(toResponse(
                        ride,
                        seats,
                        mapboxRouteDistanceService.fareForSegment(ride, segment)));
            }
        }

        return results;
    }

    // 5. GET DRIVER'S RIDES

    @Transactional(readOnly = true)
    public List<RideResponse> getMyRides(String email) {

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        DriverProfile driver = driverRepository.findByUser_Id(user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Driver profile not found"));

        return rideRepository
                .findByDriver_IdOrderByCreatedAtDesc(driver.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 6. FILTER DRIVER'S RIDES

    @Transactional(readOnly = true)
    public List<RideResponse> getMyRidesByStatus(
            String email,
            String status) {

        if (status == null || status.isBlank()) {
            return getMyRides(email);
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        DriverProfile driver = driverRepository.findByUser_Id(user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Driver profile not found"));

        return rideRepository
                .findByDriver_IdAndStatusIgnoreCaseOrderByCreatedAtDesc(
                        driver.getId(), status.trim())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 7. CONFIRM START RIDE

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

    // 8. COMPLETE TRIP

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
        ride.setGpsSharingActive(false);

        Ride completedRide = rideRepository.save(ride);

        bookingService.markPaymentsSettlementPending(
                rideId, email);

        return toResponse(completedRide);
    }

    // 9. VERIFY DRIVER OWNERSHIP

    private Ride getRideForDriver(
            String email,
            Long rideId) {

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        DriverProfile driver = driverRepository.findByUser_Id(user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Driver profile not found"));

        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Ride not found"));

        if (!ride.getDriver().getId().equals(driver.getId())) {
            throw new IllegalArgumentException(
                    "You are not authorized to update this ride");
        }

        return ride;
    }

    // 10. CONVERT ENTITY TO RESPONSE

    private RideResponse toResponse(Ride ride) {
        return toResponse(ride, ride.getAvailableSeats());
    }

    private RideResponse toResponse(Ride ride, int segmentAvailableSeats) {
        return toResponse(ride, segmentAvailableSeats, null);
    }

    private RideResponse toResponse(
            Ride ride,
            int segmentAvailableSeats,
            java.math.BigDecimal segmentPricePerSeat) {

        DriverProfile driver = ride.getDriver();

        List<RideStopResponse> stops = ride.getStops()
                .stream()
                .map(stop -> new RideStopResponse(
                        stop.getId(),
                        stop.getName(),
                        stop.getCity(),
                        stop.getLatitude(),
                        stop.getLongitude(),
                        stop.getStopOrder()
                ))
                .toList();

        return new RideResponse(
                ride.getId(),
                driver.getId(),
                driver.getUser().getFullName(),
                driver.getVehicleNumber(),

                ride.getFromLocation(),
                ride.getToLocation(),

                ride.getFromLatitude(),
                ride.getFromLongitude(),

                ride.getToLatitude(),
                ride.getToLongitude(),

                ride.getDepartureDateTime(),
                ride.getTotalSeats(),
                ride.getAvailableSeats(),
                ride.getPricePerSeat(),
                ride.getStatus(),
                driver.getPhotoUrl(),
                stops,
                segmentAvailableSeats,
                segmentPricePerSeat);
    }
}
