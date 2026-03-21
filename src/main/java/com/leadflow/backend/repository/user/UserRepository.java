package com.leadflow.backend.repository.user;

import com.leadflow.backend.entities.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /* ======================================================
       ACTIVE USERS (DEFAULT - SEMPRE USAR ESTES)
       ====================================================== */

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    /* ======================================================
       RAW ACCESS (USO RESTRITO / ADMIN / AUDITORIA)
       ====================================================== */

    Optional<User> findById(UUID id);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}