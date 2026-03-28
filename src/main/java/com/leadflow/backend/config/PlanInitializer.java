package com.leadflow.backend.config;

import com.leadflow.backend.entities.Plan;
import com.leadflow.backend.repository.PlanRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanInitializer {

    private static final Logger log = LoggerFactory.getLogger(PlanInitializer.class);

    private final PlanRepository planRepository;

    @PostConstruct
    public void initializePlan() {
        try {
            if (planRepository.count() == 0) {
                // Create Leadflow Standard plan
                Plan standardPlan = new Plan();
                standardPlan.setName("Leadflow Standard");
                standardPlan.setMaxLeads(500);
                standardPlan.setMaxUsers(10);
                standardPlan.setMaxAiExecutions(1000);
                standardPlan.setActive(true);
                planRepository.save(standardPlan);
                log.info("✅ Plan created: Leadflow Standard");

                // Create test plan "new"
                Plan newPlan = new Plan();
                newPlan.setName("new");
                newPlan.setMaxLeads(100);
                newPlan.setMaxUsers(5);
                newPlan.setMaxAiExecutions(500);
                newPlan.setActive(true);
                planRepository.save(newPlan);
                log.info("✅ Plan created: new");

                // Create additional test plans
                Plan freePlan = new Plan();
                freePlan.setName("free");
                freePlan.setMaxLeads(50);
                freePlan.setMaxUsers(2);
                freePlan.setMaxAiExecutions(100);
                freePlan.setActive(true);
                planRepository.save(freePlan);
                log.info("✅ Plan created: free");

                log.info("✅ All plans initialized successfully");
            } else {
                log.info("✅ Plans already exist in database");
            }
        } catch (Exception e) {
            log.error("❌ Error initializing plans: {}", e.getMessage(), e);
        }
    }
}
