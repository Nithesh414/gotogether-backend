package com.gotogether.backend.controller;

import com.gotogether.backend.dto.AdminDriverResponse;
import com.gotogether.backend.dto.DriverDocumentResponse;
import com.gotogether.backend.dto.DriverDocumentReviewRequest;
import com.gotogether.backend.dto.DriverVerificationRequest;

import com.gotogether.backend.entity.Admin;
import com.gotogether.backend.entity.DriverDocument;
import com.gotogether.backend.entity.DriverProfile;

import com.gotogether.backend.repository.AdminRepository;
import com.gotogether.backend.repository.DriverDocumentRepository;
import com.gotogether.backend.repository.DriverProfileRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/drivers")
public class AdminDriverController {

    private final DriverProfileRepository driverRepository;
    private final DriverDocumentRepository documentRepository;
    private final AdminRepository adminRepository;

    public AdminDriverController(
            DriverProfileRepository driverRepository,
            DriverDocumentRepository documentRepository,
            AdminRepository adminRepository) {

        this.driverRepository = driverRepository;
        this.documentRepository = documentRepository;
        this.adminRepository = adminRepository;
    }

    private AdminDriverResponse toResponse(DriverProfile driver) {
        return new AdminDriverResponse(
                driver.getId(),
                driver.getUser().getId(),
                driver.getUser().getFullName(),
                driver.getUser().getEmail(),
                driver.getUser().getMobileNumber(),
                driver.getVehicleNumber(),
                driver.getVehicleType(),
                driver.getVehicleModel(),
                driver.getAvailableSeats(),
                driver.getVerificationStatus(),
                driver.getRejectionReason(),
                driver.getCreatedAt(),
                driver.getPhotoUrl()
        );
    }

    private DriverDocumentResponse toDocumentResponse(
            DriverDocument document) {

        return new DriverDocumentResponse(
                document.getId(),
                document.getDocumentType(),
                document.getDocumentSide(),
                document.getFileFormat(),
                document.getContentType(),
                document.getFileSize(),
                document.getExpiryDate(),
                document.getReviewStatus(),
                document.getRejectionReason(),
                document.getUploadedAt(),
                document.getReviewedAt()
        );
    }

    private Optional<Admin> getAdmin(Authentication authentication) {

        if (authentication == null) {
            return Optional.empty();
        }

        return adminRepository.findByEmailIgnoreCase(
                authentication.getName()
        );
    }

    /**
     * Returns all driver profiles for the admin directory.
     * Optional status values: ALL, PENDING, APPROVED, REJECTED.
     * This endpoint is protected by the existing /api/admin/** ADMIN rule.
     */
    @GetMapping
    public ResponseEntity<?> getDrivers(
            @RequestParam(required = false, defaultValue = "ALL") String status) {

        String normalizedStatus = status == null
                ? "ALL"
                : status.trim().toUpperCase();

        List<String> allowedStatuses = List.of(
                "ALL", "PENDING", "APPROVED", "REJECTED"
        );

        if (!allowedStatuses.contains(normalizedStatus)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Status must be ALL, PENDING, APPROVED or REJECTED"
            ));
        }

        List<DriverProfile> profiles = normalizedStatus.equals("ALL")
                ? driverRepository.findAll()
                : driverRepository.findByVerificationStatusIgnoreCase(normalizedStatus);

        List<AdminDriverResponse> drivers = profiles.stream()
                .sorted(Comparator.comparing(
                        DriverProfile::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(drivers);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<AdminDriverResponse>> getPendingDrivers() {

        List<AdminDriverResponse> drivers =
                driverRepository
                        .findByVerificationStatusIgnoreCase("PENDING")
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(drivers);
    }

    @GetMapping("/{driverId}")
    public ResponseEntity<?> getDriverDetails(
            @PathVariable Long driverId) {

        Optional<DriverProfile> optionalDriver =
                driverRepository.findById(driverId);

        if (optionalDriver.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Driver not found"));
        }

        return ResponseEntity.ok(toResponse(optionalDriver.get()));
    }

    // Admin views safe document metadata only.
    @GetMapping("/{driverId}/documents")
    public ResponseEntity<?> getDriverDocuments(
            @PathVariable Long driverId) {

        if (!driverRepository.existsById(driverId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Driver not found"));
        }

        List<DriverDocumentResponse> documents =
                documentRepository.findByDriver_Id(driverId)
                        .stream()
                        .sorted(Comparator.comparing(
                                DriverDocument::getUploadedAt,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        ))
                        .map(this::toDocumentResponse)
                        .toList();

        return ResponseEntity.ok(documents);
    }

    // Admin approves or rejects an individual document.
    @PatchMapping("/{driverId}/documents/{documentId}/review")
    public ResponseEntity<?> reviewDocument(
            @PathVariable Long driverId,
            @PathVariable Long documentId,
            @RequestBody DriverDocumentReviewRequest request,
            Authentication authentication) {

        Admin admin = getAdmin(authentication).orElse(null);

        if (admin == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Admin account not found"));
        }

        if (request.status() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Review status is required"));
        }

        String status = request.status().trim().toUpperCase();

        if (!status.equals("APPROVED")
                && !status.equals("REJECTED")) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Status must be APPROVED or REJECTED"
                    ));
        }

        if (status.equals("REJECTED")
                && (request.rejectionReason() == null
                || request.rejectionReason().isBlank())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Rejection reason is required"
                    ));
        }

        Optional<DriverDocument> optionalDocument =
                documentRepository.findByIdAndDriver_Id(
                        documentId,
                        driverId
                );

        if (optionalDocument.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Driver document not found"));
        }

        DriverDocument document = optionalDocument.get();

        if (status.equals("APPROVED")
                && document.getExpiryDate() != null
                && document.getExpiryDate().isBefore(LocalDate.now())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Expired documents cannot be approved"
                    ));
        }

        document.setReviewStatus(status);
        document.setRejectionReason(
                status.equals("REJECTED")
                        ? request.rejectionReason().trim()
                        : null
        );
        document.setReviewedByAdminId(admin.getId());
        document.setReviewedAt(LocalDateTime.now());

        DriverDocument saved = documentRepository.save(document);

        return ResponseEntity.ok(Map.of(
                "message", "Document review saved successfully",
                "documentId", saved.getId(),
                "reviewStatus", saved.getReviewStatus(),
                "reviewedByAdminId", admin.getId()
        ));
    }

    // Driver approval is blocked until all mandatory documents are approved.
    private List<String> findIncompleteDocuments(Long driverId) {

        List<DriverDocument> documents =
                documentRepository.findByDriver_Id(driverId);

        List<String> requiredDocuments = List.of(
                "DRIVING_LICENCE:FRONT",
                "DRIVING_LICENCE:BACK",
                "VEHICLE_RC:FRONT",
                "VEHICLE_RC:BACK",
                "INSURANCE:SINGLE",
                "PUC:SINGLE",
                "ADDRESS_PROOF:SINGLE",
                "DRIVER_PHOTO:SINGLE"
        );

        List<String> incomplete = new java.util.ArrayList<>();

        for (String required : requiredDocuments) {

            String[] parts = required.split(":");

            String type = parts[0];
            String side = parts[1];

            DriverDocument document = documents.stream()
                    .filter(d -> type.equalsIgnoreCase(
                            d.getDocumentType()))
                    .filter(d -> side.equalsIgnoreCase(
                            d.getDocumentSide()))
                    .findFirst()
                    .orElse(null);

            if (document == null) {
                incomplete.add(required + " - NOT UPLOADED");
                continue;
            }

            if (!"APPROVED".equalsIgnoreCase(
                    document.getReviewStatus())) {
                incomplete.add(required + " - NOT APPROVED");
                continue;
            }

            boolean expiryRequired = List.of(
                    "DRIVING_LICENCE",
                    "INSURANCE",
                    "PUC"
            ).contains(type);

            if (expiryRequired && document.getExpiryDate() == null) {
                incomplete.add(required + " - EXPIRY DATE MISSING");
                continue;
            }

            if (document.getExpiryDate() != null
                    && document.getExpiryDate().isBefore(LocalDate.now())) {
                incomplete.add(required + " - EXPIRED");
            }
        }

        return incomplete;
    }

    @PatchMapping("/{driverId}/verification")
    public ResponseEntity<?> updateVerification(
            @PathVariable Long driverId,
            @RequestBody DriverVerificationRequest request,
            Authentication authentication) {

        Admin admin = getAdmin(authentication).orElse(null);

        if (admin == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Admin account not found"));
        }

        if (request.status() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Status is required"));
        }

        String status = request.status().trim().toUpperCase();

        if (!status.equals("APPROVED")
                && !status.equals("REJECTED")) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Status must be APPROVED or REJECTED"
                    ));
        }

        if (status.equals("REJECTED")
                && (request.rejectionReason() == null
                || request.rejectionReason().isBlank())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Rejection reason is required"
                    ));
        }

        Optional<DriverProfile> optionalDriver =
                driverRepository.findById(driverId);

        if (optionalDriver.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Driver not found"));
        }

        DriverProfile driver = optionalDriver.get();

        if (status.equals("APPROVED")) {

            List<String> incomplete =
                    findIncompleteDocuments(driverId);

            if (!incomplete.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "message",
                                "Driver cannot be approved. Complete document review first.",
                                "incompleteDocuments",
                                incomplete
                        ));
            }
        }

        driver.setVerificationStatus(status);

        driver.setRejectionReason(
                status.equals("REJECTED")
                        ? request.rejectionReason().trim()
                        : null
        );

        DriverProfile savedDriver =
                driverRepository.save(driver);

        return ResponseEntity.ok(Map.of(
                "message", "Driver verification updated successfully",
                "driverId", savedDriver.getId(),
                "verificationStatus",
                savedDriver.getVerificationStatus()
        ));
    }
}