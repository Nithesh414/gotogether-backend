
package com.gotogether.backend.repository;

import com.gotogether.backend.entity.PendingEmailRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingEmailRegistrationRepository
        extends JpaRepository<PendingEmailRegistration, Long> {

    Optional<PendingEmailRegistration> findByEmailIgnoreCase(
            String email
    );

    void deleteByEmailIgnoreCase(String email);
}
