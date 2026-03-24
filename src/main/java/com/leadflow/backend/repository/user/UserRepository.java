package com.leadflow.backend.repository.user;

import com.leadflow.backend.entities.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /* ======================================================
       ACTIVE USERS (SCHEMA-BASED MULTI-TENANT)
       ====================================================== */

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<User> findByEmailIgnoreCaseAndTenantIdAndDeletedAtIsNull(
            String email,
            String tenantId
    );

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    /* ======================================================
       RAW ACCESS (ADMIN / AUDITORIA)
       ====================================================== */

    Optional<User> findById(UUID id);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}