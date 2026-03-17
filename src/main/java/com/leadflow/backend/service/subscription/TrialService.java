package com.leadflow.backend.service.subscription;

import com.leadflow.backend.entities.vendor.SubscriptionStatus;
import com.leadflow.backend.entities.vendor.Vendor;
import com.leadflow.backend.entities.vendor.VendorFeatureKey;
import com.leadflow.backend.service.vendor.VendorFeatureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TrialService {

    @Value("${subscription.trial-days}")
    private int trialDays;

    private final VendorFeatureService vendorFeatureService;

    public TrialService(VendorFeatureService vendorFeatureService) {
        this.vendorFeatureService = vendorFeatureService;
    }

    public void initializeTrial(Vendor vendor) {

        Instant now = Instant.now();

        vendor.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        vendor.setSubscriptionStartedAt(now);
        vendor.setSubscriptionExpiresAt(now.plusSeconds(trialDays * 86400L));
    }

    public void enableTrialFeatures(Vendor vendor) {
        if (vendor == null || vendor.getId() == null) {
            return;
        }
        // Enable AI_CHAT feature for trial users
        vendorFeatureService.upsertFeature(vendor.getId(), VendorFeatureKey.AI_CHAT, true);
    }
}
