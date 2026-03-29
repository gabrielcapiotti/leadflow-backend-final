package com.leadflow.backend.service;

import com.leadflow.backend.entities.Plan;
import com.leadflow.backend.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;

    public Plan getActivePlan() {

        return planRepository.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("Active plan not configured"));

    }

    /**
     * Get plan by name.
     * Falls back to active plan if name not found or is null.
     * 
     * @param planName the plan name (e.g., "Leadflow Standard")
     * @return Plan or null if not found
     */
    public Plan getPlanByName(String planName) {
        if (planName == null || planName.isBlank()) {
            return getActivePlan();
        }

        return planRepository.findByNameIgnoreCase(planName)
                .orElseGet(this::getActivePlan);
    }

    /**
     * Get all available plans
     */
    public java.util.List<Plan> getAllPlans() {
        return planRepository.findAll();
    }
}
