package com.leadflow.backend.repository;

import com.leadflow.backend.entities.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {

    Optional<Plan> findByCode(String code);

    Optional<Plan> findByNameIgnoreCase(String name);

    Optional<Plan> findByActiveTrue();
}
