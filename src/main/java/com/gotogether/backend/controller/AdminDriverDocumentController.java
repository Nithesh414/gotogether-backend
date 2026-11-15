package com.gotogether.backend.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import com.gotogether.backend.entity.Admin;
import com.gotogether.backend.entity.DriverDocument;

import com.gotogether.backend.repository.AdminRepository;
import com.gotogether.backend.repository.DriverDocumentRepository;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/drivers")
public class AdminDriverDocumentController {

    private final Cloudinary cloudinary;
    private final AdminRepository adminRepository;
    private final DriverDocumentRepository documentRepository;

    public AdminDriverDocumentController(
            Cloudinary cloudinary,
            AdminRepository adminRepository,
            DriverDocumentRepository documentRepository) {

        this.cloudinary = cloudinary;
        this.adminRepository = adminRepository;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/{driverId}/documents/{documentId}/download")
    public ResponseEntity<?> getSecureDocumentUrl(
            @PathVariable Long driverId,
            @PathVariable Long documentId,
            Authentication authentication) {

        if (authentication == null ||
                !adminRepository.existsByEmailIgnoreCase(
                        authentication.getName())) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Admin authentication required"));
        }

        DriverDocument document = documentRepository
                .findByIdAndDriver_Id(documentId, driverId)
                .orElse(null);

        if (document == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Document not found"));
        }

        if (!"authenticated".equalsIgnoreCase(
                document.getDeliveryType())) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "Document is not stored with private delivery"
                    ));
        }

        String publicId = document.getCloudinaryPublicId();
        String format = document.getFileFormat();
        String resourceType = document.getResourceType();

        if (publicId == null || publicId.isBlank()
                || format == null || format.isBlank()
                || resourceType == null
                || !(resourceType.equalsIgnoreCase("image")
                || resourceType.equalsIgnoreCase("raw"))) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "message",
                            "Document storage information is incomplete"
                    ));
        }

        // The generated link expires after two minutes.
        long expiresAt =
                (System.currentTimeMillis() / 1000L) + 120L;

        try {
            String downloadUrl = cloudinary.privateDownload(
                    publicId,
                    format,
                    ObjectUtils.asMap(
                            "resource_type", resourceType,
                            "type", "authenticated",
                            "expires_at", expiresAt,
                            "attachment", false
                    )
            );

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of(
                            "downloadUrl", downloadUrl,
                            "expiresAt", expiresAt,
                            "expiresInSeconds", 120,
                            "contentType", document.getContentType()
                    ));

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "message",
                            "Unable to generate secure document access"
                    ));
        }
    }
}