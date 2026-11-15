
package com.gotogether.backend.service;

import com.gotogether.backend.dto.RegisterRequest;
import com.gotogether.backend.entity.PendingEmailRegistration;
import com.gotogether.backend.entity.User;
import com.gotogether.backend.repository.PendingEmailRegistrationRepository;
import com.gotogether.backend.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class EmailOtpService {

    private static final String POLICY_VERSION = "1.0";

    private final PendingEmailRegistrationRepository pendingRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailJsService emailJsService;

    private final SecureRandom secureRandom = new SecureRandom();

    public EmailOtpService(
            PendingEmailRegistrationRepository pendingRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailJsService emailJsService) {

        this.pendingRepository = pendingRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailJsService = emailJsService;
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void requestOtp(RegisterRequest request) {

        if (request.getFullName() == null ||
            request.getMobileNumber() == null ||
            request.getEmail() == null ||
            request.getPassword() == null ||
            request.getFullName().isBlank() ||
            request.getMobileNumber().isBlank() ||
            request.getEmail().isBlank() ||
            request.getPassword().isBlank()) {

            throw new IllegalArgumentException(
                "All registration fields are required");
        }

        // Mandatory policy consent validation
        if (!Boolean.TRUE.equals(request.getTermsAccepted())) {
            throw new IllegalArgumentException(
                "You must accept the Terms and Conditions");
        }

        if (!Boolean.TRUE.equals(request.getPrivacyAccepted())) {
            throw new IllegalArgumentException(
                "You must accept the Privacy Policy");
        }

        String name = request.getFullName().trim();
        String mobile = request.getMobileNumber().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException(
                "Enter a valid email address");
        }

        if (!mobile.matches("^\\+?[0-9]{10,15}$")) {
            throw new IllegalArgumentException(
                "Enter a valid mobile number");
        }

        if (request.getPassword().length() < 8) {
            throw new IllegalArgumentException(
                "Password must contain at least 8 characters");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                "Email already registered");
        }

        if (userRepository.existsByMobileNumber(mobile)) {
            throw new IllegalArgumentException(
                "Mobile number already registered");
        }

        LocalDateTime now = LocalDateTime.now();

        PendingEmailRegistration pending =
            pendingRepository.findByEmailIgnoreCase(email).orElse(null);

        if (pending != null &&
            pending.getLastSentAt().plusSeconds(60).isAfter(now)) {

            throw new IllegalArgumentException(
                "Please wait 60 seconds before requesting another OTP");
        }

        int otpNumber = 100000 + secureRandom.nextInt(900000);
        String otp = String.valueOf(otpNumber);

        if (pending == null) {
            pending = new PendingEmailRegistration();
        }

        pending.setFullName(name);
        pending.setMobileNumber(mobile);
        pending.setEmail(email);

        pending.setPasswordHash(
            passwordEncoder.encode(request.getPassword()));

        pending.setOtpHash(passwordEncoder.encode(otp));
        pending.setExpiresAt(now.plusMinutes(5));
        pending.setLastSentAt(now);
        pending.setAttempts(0);

        // Store accepted policies with pending registration
        pending.setTermsAccepted(true);
        pending.setPrivacyAccepted(true);
        pending.setPolicyVersion(POLICY_VERSION);
        pending.setConsentAcceptedAt(now);

        pendingRepository.save(pending);

        emailJsService.sendOtp(email, name, otp);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public User verifyOtp(String emailInput, String otp) {

        if (emailInput == null || emailInput.isBlank() ||
            otp == null || !otp.matches("^[0-9]{6}$")) {

            throw new IllegalArgumentException(
                "Enter a valid email address and 6-digit OTP");
        }

        String email = emailInput.trim().toLowerCase(Locale.ROOT);

        PendingEmailRegistration pending =
            pendingRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException(
                    "No pending registration found. Request a new OTP"));

        LocalDateTime now = LocalDateTime.now();

        if (now.isAfter(pending.getExpiresAt())) {
            pendingRepository.delete(pending);

            throw new IllegalArgumentException(
                "OTP expired. Please request a new OTP");
        }

        if (pending.getAttempts() >= 5) {
            throw new IllegalArgumentException(
                "Maximum OTP attempts reached. Request a new OTP");
        }

        if (!passwordEncoder.matches(otp, pending.getOtpHash())) {
            pending.setAttempts(pending.getAttempts() + 1);
            pendingRepository.save(pending);

            throw new IllegalArgumentException("Incorrect OTP");
        }

        // Verify consent again before account creation
        if (!Boolean.TRUE.equals(pending.getTermsAccepted()) ||
            !Boolean.TRUE.equals(pending.getPrivacyAccepted()) ||
            pending.getPolicyVersion() == null ||
            pending.getConsentAcceptedAt() == null) {

            throw new IllegalArgumentException(
                "Policy acceptance is missing. Please register again");
        }

        if (userRepository.existsByEmail(email) ||
            userRepository.existsByMobileNumber(
                pending.getMobileNumber())) {

            pendingRepository.delete(pending);

            throw new IllegalArgumentException(
                "Email or mobile number is already registered");
        }

        User user = new User();

        user.setFullName(pending.getFullName());
        user.setMobileNumber(pending.getMobileNumber());
        user.setEmail(pending.getEmail());
        user.setPassword(pending.getPasswordHash());

        // Transfer consent to permanent user record
        user.setTermsAccepted(pending.getTermsAccepted());
        user.setPrivacyAccepted(pending.getPrivacyAccepted());
        user.setPolicyVersion(pending.getPolicyVersion());
        user.setConsentAcceptedAt(pending.getConsentAcceptedAt());

        User savedUser = userRepository.save(user);

        pendingRepository.delete(pending);

        return savedUser;
    }
}
