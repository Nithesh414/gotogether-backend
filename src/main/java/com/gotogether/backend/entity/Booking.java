package com.gotogether.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private User passenger;

    @Column(name = "seats_booked", nullable = false)
    private Integer seatsBooked;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    // Selected passenger journey.
    // Null values represent legacy full-route bookings.
    @Column(name = "pickup_location")
    private String pickupLocation;

    @Column(name = "dropoff_location")
    private String dropoffLocation;

    @Column(name = "pickup_stop_order")
    private Integer pickupStopOrder;

    @Column(name = "dropoff_stop_order")
    private Integer dropoffStopOrder;

    @Column(nullable = false)
    private String status = "CONFIRMED";

    // Simulated payment fields - no real money is processed.
    @Column(name = "payment_status")
    private String paymentStatus = "SIMULATED_PENDING";

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // Boarding OTP fields.
    @Column(name = "boarding_otp_hash")
    private String boardingOtpHash;

    @Column(name = "boarding_otp_expires_at")
    private LocalDateTime boardingOtpExpiresAt;

    @Column(name = "boarding_otp_attempts", nullable = false)
    private Integer boardingOtpAttempts = 0;

    @Column(name = "boarded_at")
    private LocalDateTime boardedAt;

    private LocalDateTime createdAt;
    private LocalDateTime cancelledAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Ride getRide() { return ride; }
    public void setRide(Ride ride) { this.ride = ride; }

    public User getPassenger() { return passenger; }
    public void setPassenger(User passenger) { this.passenger = passenger; }

    public Integer getSeatsBooked() { return seatsBooked; }
    public void setSeatsBooked(Integer seatsBooked) {
        this.seatsBooked = seatsBooked;
    }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public String getDropoffLocation() { return dropoffLocation; }
    public void setDropoffLocation(String dropoffLocation) {
        this.dropoffLocation = dropoffLocation;
    }

    public Integer getPickupStopOrder() { return pickupStopOrder; }
    public void setPickupStopOrder(Integer pickupStopOrder) {
        this.pickupStopOrder = pickupStopOrder;
    }

    public Integer getDropoffStopOrder() { return dropoffStopOrder; }
    public void setDropoffStopOrder(Integer dropoffStopOrder) {
        this.dropoffStopOrder = dropoffStopOrder;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public String getBoardingOtpHash() { return boardingOtpHash; }
    public void setBoardingOtpHash(String boardingOtpHash) {
        this.boardingOtpHash = boardingOtpHash;
    }

    public LocalDateTime getBoardingOtpExpiresAt() {
        return boardingOtpExpiresAt;
    }

    public void setBoardingOtpExpiresAt(LocalDateTime boardingOtpExpiresAt) {
        this.boardingOtpExpiresAt = boardingOtpExpiresAt;
    }

    public Integer getBoardingOtpAttempts() { return boardingOtpAttempts; }
    public void setBoardingOtpAttempts(Integer boardingOtpAttempts) {
        this.boardingOtpAttempts = boardingOtpAttempts;
    }

    public LocalDateTime getBoardedAt() { return boardedAt; }
    public void setBoardedAt(LocalDateTime boardedAt) {
        this.boardedAt = boardedAt;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}