
package com.gotogether.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.gotogether.backend.entity.DriverProfile;
import com.gotogether.backend.entity.User;
import com.gotogether.backend.repository.DriverProfileRepository;
import com.gotogether.backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Map;

@Service
public class DriverPhotoService {

    private final Cloudinary cloudinary;
    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;

    public DriverPhotoService(
        Cloudinary cloudinary,
        UserRepository userRepository,
        DriverProfileRepository driverProfileRepository
    ) {
        this.cloudinary = cloudinary;
        this.userRepository = userRepository;
        this.driverProfileRepository = driverProfileRepository;
    }

    @Transactional
    public String uploadDriverPhoto(
        String email,
        MultipartFile photo
    ) {
        if (photo == null || photo.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Please select a photo"
            );
        }

        if (photo.getSize() > 1L * 1024 * 1024) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Photo size must not exceed 1 MB"
            );
        }

        String contentType = photo.getContentType();

        if (contentType == null ||
            !(contentType.equalsIgnoreCase("image/jpeg") ||
              contentType.equalsIgnoreCase("image/png") ||
              contentType.equalsIgnoreCase("image/webp"))) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only JPG, PNG and WEBP images are allowed"
            );
        }

        try {
            if (ImageIO.read(photo.getInputStream()) == null) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid image file"
                );
            }

            User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "User not found"
                ));

            DriverProfile driver = driverProfileRepository
                .findByUser_Id(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Driver profile not found"
                ));

            Map uploadResult = cloudinary.uploader().upload(
                photo.getBytes(),
                ObjectUtils.asMap(
                    "folder", "gotogether/drivers",
                    "public_id", "driver_" + driver.getId(),
                    "overwrite", true,
                    "resource_type", "image"
                )
            );

            String photoUrl = uploadResult.get("secure_url").toString();

            driver.setPhotoUrl(photoUrl);
            driverProfileRepository.save(driver);

            return photoUrl;

        } catch (IOException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unable to read the uploaded image"
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {

    System.err.println("========== CLOUDINARY UPLOAD ERROR ==========");
    System.err.println("Exception: " + ex.getClass().getName());
    System.err.println("Message: " + ex.getMessage());
    ex.printStackTrace();
    System.err.println("==============================================");

    throw new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "Unable to upload photo to Cloudinary"
    );
        }
    }
}
