package com.leadflow.backend.repository.settings;

import com.leadflow.backend.entities.Setting;
import com.leadflow.backend.entities.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettingRepository extends JpaRepository<Setting, UUID> {

    /* ======================================================
       CONSULTA PRINCIPAL
       ====================================================== */

    @EntityGraph(attributePaths = {"user"})
    Optional<Setting> findByUser(User user);

    /* ======================================================
       CONSULTA COM SOFT DELETE
       ====================================================== */

    @Query("SELECT s FROM Setting s WHERE s.user = :user")
    Optional<Setting> findByUserIncludingDeleted(@Param("user") User user);

    /* ======================================================
       VALIDAÇÕES
       ====================================================== */

    boolean existsByUser(User user);
}
