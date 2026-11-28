package com.gotogether.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "ride_stops",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_ride_stop_order",
            columnNames = {"ride_id", "stop_order"}
        )
    }
)
public class RideStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 150)
    private String city;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    public Long getId() { return id; }

    public Ride getRide() { return ride; }

    public void setRide(Ride ride) { this.ride = ride; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getCity() { return city; }

    public void setCity(String city) { this.city = city; }

    public Double getLatitude() { return latitude; }

    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }

    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Integer getStopOrder() { return stopOrder; }

    public void setStopOrder(Integer stopOrder) { this.stopOrder = stopOrder; }
}
