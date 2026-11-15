
package com.gotogether.backend.controller;

import com.gotogether.backend.service.DriverPhotoService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/drivers")
public class DriverPhotoController {

    private final DriverPhotoService driverPhotoService;

    public DriverPhotoController(
        DriverPhotoService driverPhotoService
    ) {
        this.driverPhotoService = driverPhotoService;
    }

    @PostMapping(
        value = "/me/photo",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadPhoto(
        @RequestParam("photo") MultipartFile photo,
        Authentication authentication
    ) {
        String photoUrl = driverPhotoService.uploadDriverPhoto(
            authentication.getName(),
            photo
        );

        return ResponseEntity.ok(
            Map.of(
                "message", "Driver photo uploaded successfully",
                "photoUrl", photoUrl
            )
        );
    }
}
