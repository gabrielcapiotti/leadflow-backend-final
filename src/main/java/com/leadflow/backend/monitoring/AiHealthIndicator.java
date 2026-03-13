package com.leadflow.backend.monitoring;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

// @Component // DESABILITADO: causava delay no boot
public class AiHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {

        try {
            return Health.up().withDetail("ai", "ok").build();
        } catch (Exception e) {
            return Health.down().withDetail("ai", "erro").build();
        }
    }
}
