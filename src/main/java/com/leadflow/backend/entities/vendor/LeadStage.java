package com.leadflow.backend.entities.vendor;

import java.util.Set;

public enum LeadStage {

    NEW,
    CONTACT,
    NEGOTIATION,
    CLOSED,
    LOST;

    public boolean canTransitionTo(LeadStage target) {

        return switch (this) {

            case NEW ->
                    Set.of(CONTACT, LOST).contains(target);

            case CONTACT ->
                    Set.of(NEGOTIATION, LOST).contains(target);

            case NEGOTIATION ->
                    Set.of(CLOSED, LOST).contains(target);

            case CLOSED ->
                    false;

            case LOST ->
                    false;
        };
    }
}