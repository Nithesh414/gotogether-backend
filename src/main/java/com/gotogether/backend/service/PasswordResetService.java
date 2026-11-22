package com.gotogether.backend.service;

import com.gotogether.backend.entity.PasswordResetOtp;
import com.gotogether.backend.entity.User;
import com.gotogether.backend.repository.PasswordResetOtpRepository;
import com.gotogether.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PasswordResetService {
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetOtpRepository resetRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailJsService emailJsService;

    public PasswordResetService(PasswordResetOtpRepository resetRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                EmailJsService emailJsService) {
        this.resetRepository = resetRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailJsService = emailJsService;
    }

    @Transactional
    public void requestOtp(String emailInput) {
        if (emailInput == null || !emailInput.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        String email = emailInput.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        // Always return the same public response, whether the account exists or not.
        if (user == null) return;

        LocalDateTime now = LocalDateTime.now();
        PasswordResetOtp reset = resetRepository.findByEmailIgnoreCase(email).orElseGet(PasswordResetOtp::new);
        if (reset.getLastSentAt() != null && reset.getLastSentAt().plusSeconds(60).isAfter(now)) {
            return; // Keep the public response identical; do not reveal account existence.
        }

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        reset.setEmail(email);
        reset.setOtpHash(passwordEncoder.encode(otp));
        reset.setExpiresAt(now.plusMinutes(5));
        reset.setLastSentAt(now);
        reset.setAttempts(0);
        reset.setResetTokenHash(null);
        reset.setResetTokenExpiresAt(null);
        resetRepository.save(reset);
        emailJsService.sendOtp(email, user.getFullName(), otp);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public String verifyOtp(String emailInput, String otp) {
        String email = normalizeEmail(emailInput);
        if (otp == null || !otp.matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("Enter the 6-digit OTP");
        }
        PasswordResetOtp reset = resetRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("OTP is invalid or expired. Request a new one."));
        LocalDateTime now = LocalDateTime.now();
        if (reset.getExpiresAt().isBefore(now)) {
            resetRepository.delete(reset);
            throw new IllegalArgumentException("OTP expired. Please request a new OTP.");
        }
        if (reset.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Maximum OTP attempts reached. Request a new OTP.");
        }
        if (!passwordEncoder.matches(otp, reset.getOtpHash())) {
            reset.setAttempts(reset.getAttempts() + 1);
            resetRepository.save(reset);
            throw new IllegalArgumentException("Incorrect OTP");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        reset.setResetTokenHash(sha256(rawToken));
        reset.setResetTokenExpiresAt(now.plusMinutes(10));
        resetRepository.save(reset);
        return rawToken;
    }

    @Transactional
    public void resetPassword(String emailInput, String resetToken, String newPassword) {
        String email = normalizeEmail(emailInput);
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72) {
            throw new IllegalArgumentException("Password must contain 8 to 72 characters");
        }
        if (resetToken == null || resetToken.isBlank()) {
            throw new IllegalArgumentException("Verify your OTP before resetting the password");
        }
        PasswordResetOtp reset = resetRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Password reset session expired. Start again."));
        if (reset.getResetTokenHash() == null || reset.getResetTokenExpiresAt() == null ||
                reset.getResetTokenExpiresAt().isBefore(LocalDateTime.now()) ||
                !MessageDigest.isEqual(reset.getResetTokenHash().getBytes(StandardCharsets.UTF_8),
                        sha256(resetToken).getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Password reset session is invalid or expired. Verify your OTP again.");
        }
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Unable to reset password for this account"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        resetRepository.delete(reset); // one-time reset token
    }

    private String normalizeEmail(String input) {
        if (input == null || !input.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        return input.trim().toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to secure password reset token", exception);
        }
    }
}
