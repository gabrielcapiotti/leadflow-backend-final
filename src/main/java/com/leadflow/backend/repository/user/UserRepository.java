package com.leadflow.backend.repository.user;

import com.leadflow.backend.entities.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /* ======================================================
       AUTENTICAÇÃO (APENAS USUÁRIOS ATIVOS)
       ====================================================== */

    @Query(value = "SELECT u.* FROM public.users u WHERE LOWER(u.email) = LOWER(:email) AND u.deleted_at IS NULL", nativeQuery = true)
    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(@Param("email") String email);

    /* ======================================================
       VALIDAÇÃO DE CADASTRO
       ====================================================== */

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    /* ======================================================
       CONSULTAS ADMINISTRATIVAS
       ====================================================== */

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByEmail(String email);

    Page<User> findByDeletedAtIsNull(Pageable pageable);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmailAndDeletedAtIsNull(String email);
}