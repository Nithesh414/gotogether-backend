package com.gotogether.backend.controller;

import com.gotogether.backend.dto.AdminBookingResponse;
import com.gotogether.backend.dto.AdminOverviewResponse;
import com.gotogether.backend.dto.AdminRideResponse;
import com.gotogether.backend.dto.AdminUserResponse;
import com.gotogether.backend.entity.Booking;
import com.gotogether.backend.entity.DriverProfile;
import com.gotogether.backend.entity.Ride;
import com.gotogether.backend.entity.User;
import com.gotogether.backend.repository.BookingRepository;
import com.gotogether.backend.repository.DriverProfileRepository;
import com.gotogether.backend.repository.RideRepository;
import com.gotogether.backend.repository.UserRepository;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@Transactional(readOnly = true)
public class AdminManagementController {

    private final UserRepository userRepository;
    private final DriverProfileRepository driverRepository;
    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;

    public AdminManagementController(
            UserRepository userRepository,
            DriverProfileRepository driverRepository,
            RideRepository rideRepository,
            BookingRepository bookingRepository) {
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.rideRepository = rideRepository;
        this.bookingRepository = bookingRepository;
    }

    @GetMapping("/overview")
    public AdminOverviewResponse getOverview() {
        List<User> users = userRepository.findAll();
        List<DriverProfile> drivers = driverRepository.findAll();
        List<Ride> rides = rideRepository.findAll();
        List<Booking> bookings = bookingRepository.findAll();

        long activeRides = rides.stream()
                .filter(r -> List.of("BOARDING", "IN_PROGRESS")
                        .contains(upper(r.getStatus())))
                .count();

        BigDecimal value = bookings.stream()
                .filter(b -> {
                    String payment = upper(b.getPaymentStatus());
                    return payment.equals("SIMULATED_PAID")
                            || payment.equals("SETTLEMENT_PENDING");
                })
                .map(Booking::getTotalAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AdminOverviewResponse(
                users.size(),
                drivers.size(),
                countDriverStatus(drivers, "PENDING"),
                countDriverStatus(drivers, "APPROVED"),
                countDriverStatus(drivers, "REJECTED"),
                rides.size(),
                activeRides,
                countRideStatus(rides, "COMPLETED"),
                countRideStatus(rides, "CANCELLED"),
                bookings.size(),
                countBookingStatus(bookings, "CONFIRMED"),
                countBookingStatus(bookings, "CANCELLED"),
                value
        );
    }

    @GetMapping("/users")
    public List<AdminUserResponse> getUsers(
            @RequestParam(required = false) String search) {
        Map<Long, DriverProfile> driverByUser = driverRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        d -> d.getUser().getId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));

        String query = normalize(search);
        return userRepository.findAll().stream()
                .filter(u -> query.isEmpty()
                        || contains(u.getFullName(), query)
                        || contains(u.getEmail(), query)
                        || contains(u.getMobileNumber(), query))
                .sorted(Comparator.comparing(
                        User::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(u -> {
                    DriverProfile driver = driverByUser.get(u.getId());
                    return new AdminUserResponse(
                            u.getId(),
                            u.getFullName(),
                            u.getEmail(),
                            u.getMobileNumber(),
                            u.getRole(),
                            u.getCreatedAt(),
                            driver == null ? null : driver.getId(),
                            driver == null ? null : driver.getVerificationStatus()
                    );
                })
                .toList();
    }

    @GetMapping("/rides")
    public List<AdminRideResponse> getRides(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        String requestedStatus = upper(status);
        String query = normalize(search);

        return rideRepository.findAll().stream()
                .filter(r -> requestedStatus.isEmpty()
                        || requestedStatus.equals(upper(r.getStatus())))
                .filter(r -> query.isEmpty()
                        || contains(r.getFromLocation(), query)
                        || contains(r.getToLocation(), query)
                        || contains(r.getDriver().getUser().getFullName(), query)
                        || contains(r.getDriver().getVehicleNumber(), query))
                .sorted(Comparator.comparing(
                        Ride::getDepartureDateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(r -> new AdminRideResponse(
                        r.getId(),
                        r.getDriver().getId(),
                        r.getDriver().getUser().getFullName(),
                        r.getDriver().getUser().getEmail(),
                        r.getDriver().getVehicleNumber(),
                        r.getFromLocation(),
                        r.getToLocation(),
                        r.getDepartureDateTime(),
                        r.getTotalSeats(),
                        r.getAvailableSeats(),
                        r.getPricePerSeat(),
                        r.getStatus(),
                        r.getCreatedAt(),
                        r.isGpsSharingActive()
                ))
                .toList();
    }

    @GetMapping("/bookings")
    public List<AdminBookingResponse> getBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        String requestedStatus = upper(status);
        String query = normalize(search);

        return bookingRepository.findAll().stream()
                .filter(b -> requestedStatus.isEmpty()
                        || requestedStatus.equals(upper(b.getStatus()))
                        || requestedStatus.equals(upper(b.getPaymentStatus())))
                .filter(b -> query.isEmpty()
                        || contains(b.getPassenger().getFullName(), query)
                        || contains(b.getPassenger().getEmail(), query)
                        || contains(b.getPaymentReference(), query)
                        || contains(b.getRide().getFromLocation(), query)
                        || contains(b.getRide().getToLocation(), query))
                .sorted(Comparator.comparing(
                        Booking::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(b -> new AdminBookingResponse(
                        b.getId(),
                        b.getRide().getId(),
                        b.getRide().getFromLocation(),
                        b.getRide().getToLocation(),
                        b.getRide().getDepartureDateTime(),
                        b.getPassenger().getId(),
                        b.getPassenger().getFullName(),
                        b.getPassenger().getEmail(),
                        b.getPassenger().getMobileNumber(),
                        b.getRide().getDriver().getUser().getFullName(),
                        b.getSeatsBooked(),
                        b.getTotalAmount(),
                        b.getStatus(),
                        b.getPaymentStatus(),
                        b.getPaymentReference(),
                        b.getCreatedAt(),
                        b.getCancelledAt()
                ))
                .toList();
    }

    private long countDriverStatus(List<DriverProfile> values, String status) {
        return values.stream().filter(d -> status.equals(upper(d.getVerificationStatus()))).count();
    }

    private long countRideStatus(List<Ride> values, String status) {
        return values.stream().filter(r -> status.equals(upper(r.getStatus()))).count();
    }

    private long countBookingStatus(List<Booking> values, String status) {
        return values.stream().filter(b -> status.equals(upper(b.getStatus()))).count();
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
