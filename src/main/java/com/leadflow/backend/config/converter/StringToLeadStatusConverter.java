package com.leadflow.backend.config.converter;

import com.leadflow.backend.entities.enums.LeadStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converter to handle string to LeadStatus enum conversion in request parameters
 */
@Component
public class StringToLeadStatusConverter implements Converter<String, LeadStatus> {

    @Override
    public LeadStatus convert(String source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("LeadStatus cannot be null or empty");
        }

        try {
            return LeadStatus.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid LeadStatus: " + source + ". Valid values are: " + String.join(", ", getValidValues()),
                e
            );
        }
    }

    private String[] getValidValues() {
        LeadStatus[] values = LeadStatus.values();
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i].name();
        }
        return result;
    }
}
