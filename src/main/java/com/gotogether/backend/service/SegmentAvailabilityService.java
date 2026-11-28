package com.gotogether.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.gotogether.backend.entity.Booking;
import com.gotogether.backend.entity.Ride;
import com.gotogether.backend.entity.RideStop;
import com.gotogether.backend.repository.BookingRepository;

@Service
public class SegmentAvailabilityService {

    private final BookingRepository bookingRepository;

    public SegmentAvailabilityService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public record Segment(
            int pickupOrder,
            int dropoffOrder,
            String pickupLocation,
            String dropoffLocation) {
    }

    private record RoutePoint(
            int order,
            String label,
            List<String> aliases) {
    }

    private int stopCount(Ride ride) {
        return ride.getStops() == null ? 0 : ride.getStops().size();
    }

    private List<RoutePoint> routePoints(Ride ride) {
        List<RoutePoint> points = new ArrayList<>();

        points.add(new RoutePoint(
                0,
                ride.getFromLocation(),
                List.of(ride.getFromLocation())));

        List<RideStop> stops = ride.getStops() == null
                ? List.of()
                : ride.getStops().stream()
                    .sorted(Comparator.comparing(
                            RideStop::getStopOrder,
                            Comparator.nullsLast(Integer::compareTo)))
                    .toList();

        for (RideStop stop : stops) {
            List<String> aliases = new ArrayList<>();

            if (stop.getCity() != null) {
                aliases.add(stop.getCity());
            }

            if (stop.getName() != null
                    && !stop.getName().equalsIgnoreCase(stop.getCity())) {
                aliases.add(stop.getName());
            }

            points.add(new RoutePoint(
                    stop.getStopOrder(),
                    stop.getCity(),
                    aliases));
        }

        int destinationOrder = stops.size() + 1;

        points.add(new RoutePoint(
                destinationOrder,
                ride.getToLocation(),
                List.of(ride.getToLocation())));

        return points;
    }

    public Segment resolve(
            Ride ride,
            String requestedFrom,
            String requestedTo) {

        List<RoutePoint> points = routePoints(ride);

        // Compatibility with existing clients that send no segment.
        if ((requestedFrom == null || requestedFrom.isBlank())
                && (requestedTo == null || requestedTo.isBlank())) {

            RoutePoint first = points.get(0);
            RoutePoint last = points.get(points.size() - 1);

            return new Segment(
                    first.order(),
                    last.order(),
                    first.label(),
                    last.label());
        }

        if (requestedFrom == null || requestedFrom.isBlank()
                || requestedTo == null || requestedTo.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Select both pickup and destination locations");
        }

        RoutePoint from = findPoint(points, requestedFrom.trim());
        RoutePoint to = findPoint(points, requestedTo.trim());

        if (from.order() >= to.order()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Your destination must come after your pickup point on this route");
        }

        return new Segment(
                from.order(),
                to.order(),
                from.label(),
                to.label());
    }

    private RoutePoint findPoint(List<RoutePoint> points, String requested) {
        String value = requested.toLowerCase(Locale.ROOT);

        List<RoutePoint> exact = points.stream()
                .filter(point -> point.aliases().stream()
                        .anyMatch(alias -> alias != null
                                && alias.trim().equalsIgnoreCase(value)))
                .toList();

        if (exact.size() == 1) {
            return exact.get(0);
        }

        if (exact.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The selected location is ambiguous. Choose a more specific stop");
        }

        List<RoutePoint> partial = points.stream()
                .filter(point -> point.aliases().stream()
                        .anyMatch(alias -> alias != null
                                && alias.toLowerCase(Locale.ROOT).contains(value)))
                .toList();

        if (partial.size() == 1) {
            return partial.get(0);
        }

        if (partial.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The selected location matches multiple stops. Choose a more specific location");
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "The selected location is not part of this ride");
    }

    public int availableSeats(Ride ride, int pickupOrder, int dropoffOrder) {
        if (pickupOrder >= dropoffOrder) {
            return 0;
        }

        List<Booking> bookings =
                bookingRepository.findByRide_IdOrderByCreatedAtDesc(ride.getId());

        int minimumAvailable = ride.getTotalSeats();

        for (int segment = pickupOrder; segment < dropoffOrder; segment++) {
            int occupied = 0;

            for (Booking booking : bookings) {
                String status = booking.getStatus();

                if (!"CONFIRMED".equalsIgnoreCase(status)
                        && !"BOARDED".equalsIgnoreCase(status)) {
                    continue;
                }

                Integer start = booking.getPickupStopOrder();
                Integer end = booking.getDropoffStopOrder();

                // Legacy bookings occupy the complete route.
                if (start == null || end == null) {
                    start = 0;
                    end = stopCount(ride) + 1;
                }

                if (start <= segment && end > segment) {
                    occupied += booking.getSeatsBooked() == null
                            ? 0 : booking.getSeatsBooked();
                }
            }

            int remaining = Math.max(0, ride.getTotalSeats() - occupied);
            minimumAvailable = Math.min(minimumAvailable, remaining);
        }

        return minimumAvailable;
    }

    public int availableSeats(Ride ride, Segment segment) {
        return availableSeats(
                ride,
                segment.pickupOrder(),
                segment.dropoffOrder());
    }

    public void recalculateRideAvailableSeats(Ride ride) {
        int finalOrder = stopCount(ride) + 1;

        int remaining = availableSeats(ride, 0, finalOrder);

        ride.setAvailableSeats(remaining);
    }
}