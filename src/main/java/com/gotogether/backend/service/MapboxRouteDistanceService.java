package com.gotogether.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.gotogether.backend.entity.Ride;
import com.gotogether.backend.entity.RideStop;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

@Service
public class MapboxRouteDistanceService {

    private final String accessToken;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public MapboxRouteDistanceService(
            @Value("${app.mapbox.access-token:}") String accessToken,
            ObjectMapper objectMapper) {
        this.accessToken = accessToken;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    private record RoutePoint(
            int order,
            Double latitude,
            Double longitude) {
    }

    public BigDecimal fareForSegment(
            Ride ride,
            SegmentAvailabilityService.Segment segment) {

        int finalOrder = ride.getStops() == null
                ? 1 : ride.getStops().size() + 1;

        BigDecimal fullFare = ride.getPricePerSeat();

        if (fullFare == null || fullFare.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid ride fare");
        }

        // Full-route bookings retain the driver's original fare.
        if (segment.pickupOrder() == 0
                && segment.dropoffOrder() == finalOrder) {
            return fullFare.setScale(2, RoundingMode.HALF_UP);
        }

        List<RoutePoint> points = getRoutePoints(ride);

        if (points.size() < 2
                || segment.pickupOrder() < 0
                || segment.dropoffOrder() >= points.size()
                || segment.pickupOrder() >= segment.dropoffOrder()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid journey segment");
        }

        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Route fare calculation is not configured");
        }

        StringBuilder coordinates = new StringBuilder();

        for (RoutePoint point : points) {
            if (!isValidCoordinate(point.latitude(), point.longitude())) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Route coordinates are incomplete");
            }

            if (!coordinates.isEmpty()) {
                coordinates.append(";");
            }

            // Mapbox coordinate order: longitude,latitude.
            coordinates.append(point.longitude())
                    .append(",")
                    .append(point.latitude());
        }

        String encodedToken = UriUtils.encodeQueryParam(
                accessToken, StandardCharsets.UTF_8);

        String url = "https://api.mapbox.com/directions/v5/mapbox/driving/"
                + coordinates
                + "?overview=false&steps=false&access_token="
                + encodedToken;

        try {
            String response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

         if (response == null || response.isBlank()) {
    throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Driving-distance service returned an empty response");
}

JsonNode root = objectMapper.readTree(response);

if (root == null || root.isNull() || root.isMissingNode()) {
    throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Invalid driving-distance response");
}

JsonNode routes = root.path("routes");

            if (!routes.isArray() || routes.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Unable to calculate the driving route");
            }

            JsonNode legs = routes.get(0).path("legs");

            if (!legs.isArray() || legs.size() != points.size() - 1) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Mapbox returned incomplete route distances");
            }

            double fullDistance = 0;
            double selectedDistance = 0;

            for (int i = 0; i < legs.size(); i++) {
                double distance = legs.get(i).path("distance").asDouble(-1);

                if (!Double.isFinite(distance) || distance <= 0) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Invalid driving distance received");
                }

                fullDistance += distance;

                if (i >= segment.pickupOrder()
                        && i < segment.dropoffOrder()) {
                    selectedDistance += distance;
                }
            }

            if (fullDistance <= 0 || selectedDistance <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Unable to calculate the selected journey fare");
            }

            return fullFare
                    .multiply(BigDecimal.valueOf(selectedDistance))
                    .divide(
                            BigDecimal.valueOf(fullDistance),
                            8,
                            RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP);

        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Driving-distance service is temporarily unavailable");
        } catch (tools.jackson.core.JacksonException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to read the driving-distance response");
        }
    }

    private List<RoutePoint> getRoutePoints(Ride ride) {
        List<RoutePoint> points = new ArrayList<>();

        points.add(new RoutePoint(
                0, ride.getFromLatitude(), ride.getFromLongitude()));

        List<RideStop> stops = ride.getStops() == null
                ? List.of()
                : ride.getStops().stream()
                    .sorted(Comparator.comparing(
                            RideStop::getStopOrder,
                            Comparator.nullsLast(Integer::compareTo)))
                    .toList();

        for (RideStop stop : stops) {
            if (stop.getStopOrder() == null) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "An intermediate stop has an invalid route order");
            }

            points.add(new RoutePoint(
                    stop.getStopOrder(),
                    stop.getLatitude(),
                    stop.getLongitude()));
        }

        points.add(new RoutePoint(
                stops.size() + 1,
                ride.getToLatitude(),
                ride.getToLongitude()));

        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).order() != i) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The ride route has an invalid stop sequence");
            }
        }

        return points;
    }

    private boolean isValidCoordinate(Double latitude, Double longitude) {
        return latitude != null
                && longitude != null
                && Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180;
    }
}
