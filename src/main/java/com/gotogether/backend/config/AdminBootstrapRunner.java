
package com.gotogether.backend.config;

import com.gotogether.backend.entity.Admin;
import com.gotogether.backend.repository.AdminRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    public AdminBootstrapRunner(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {

        String envEmail = System.getenv("APP_ADMIN_EMAIL");
        String envPassword = System.getenv("APP_ADMIN_PASSWORD");

        System.out.println("--- ADMIN BOOTSTRAP DIAGNOSTICS ---");
        System.out.println("Environment email received: " +
                (envEmail != null && !envEmail.isBlank()));
        System.out.println("Environment password received: " +
                (envPassword != null && !envPassword.isBlank()));
        System.out.println("Environment password length valid: " +
                (envPassword != null && envPassword.length() >= 12));
        System.out.println("Spring email received: " +
                (adminEmail != null && !adminEmail.isBlank()));
        System.out.println("Spring password length valid: " +
                (adminPassword != null && adminPassword.length() >= 12));
        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null
                || adminPassword.length() < 12) {

            System.out.println(
                "ADMIN BOOTSTRAP SKIPPED: Check environment variables."
            );
            return;
        }

        String email = adminEmail.trim().toLowerCase();

        if (adminRepository.existsByEmailIgnoreCase(email)) {
            System.out.println("ADMIN ACCOUNT ALREADY EXISTS.");
            return;
        }

        Admin admin = new Admin();
        admin.setEmail(email);
        admin.setFullName("GoTogether Administrator");
        admin.setPassword(passwordEncoder.encode(adminPassword));

        adminRepository.save(admin);

        System.out.println(
            "ADMIN ACCOUNT CREATED SUCCESSFULLY."
        );
    }
}
