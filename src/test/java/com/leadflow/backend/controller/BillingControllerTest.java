package com.leadflow.backend.controller;

import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.security.SubscriptionGuard;
import com.leadflow.backend.security.VendorContext;
import com.leadflow.backend.service.billing.StripeService;
import com.leadflow.backend.service.billing.StripeWebhookAlertService;
import com.leadflow.backend.service.billing.StripeWebhookValidator;
import com.leadflow.backend.service.vendor.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = BillingController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = ".*Interceptor|.*Filter"
    )
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StripeService stripeService;

    @MockBean
    private StripeWebhookValidator webhookValidator;

    @MockBean
    private StripeWebhookAlertService webhookAlertService;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private VendorContext vendorContext;

    @MockBean
    private SubscriptionGuard subscriptionGuard;

    private UUID vendorId;
    private Subscription mockSubscription;

    @BeforeEach
    void setUp() {
        vendorId = UUID.randomUUID();
        
        mockSubscription = new Subscription();
        mockSubscription.setTenantId(vendorId);
        mockSubscription.setStripeCustomerId("cus_test123");
        mockSubscription.setStripeSubscriptionId("sub_test123");
        mockSubscription.setEmail("vendor@test.com");
        mockSubscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        mockSubscription.setStartedAt(LocalDateTime.now().minusMonths(1));
        mockSubscription.setExpiresAt(LocalDateTime.now().plusDays(30));
    }

    @Test
    @WithMockUser(username = "vendor@test.com")
    void getSubscriptionDetails_ShouldReturnSubscriptionInfo() throws Exception {
        when(vendorContext.getCurrentVendorId()).thenReturn(vendorId);
        when(subscriptionService.getSubscriptionByVendorId(vendorId))
            .thenReturn(Optional.of(mockSubscription));

        mockMvc.perform(get("/billing/subscription"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.stripe_customer_id").value("cus_test123"))
            .andExpect(jsonPath("$.email").value("vendor@test.com"));
    }

    @Test
    @WithMockUser(username = "vendor@test.com")
    void getSubscriptionDetails_NotFound_ShouldReturn404() throws Exception {
        when(vendorContext.getCurrentVendorId()).thenReturn(vendorId);
        when(subscriptionService.getSubscriptionByVendorId(vendorId))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/billing/subscription"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "vendor@test.com")
    void getInvoices_ShouldReturnEmptyListWhenNoStripeCustomer() throws Exception {
        Subscription noCustomerSub = new Subscription();
        noCustomerSub.setTenantId(vendorId);
        noCustomerSub.setStripeCustomerId(null);

        when(vendorContext.getCurrentVendorId()).thenReturn(vendorId);
        when(subscriptionService.getSubscriptionByVendorId(vendorId))
            .thenReturn(Optional.of(noCustomerSub));

        mockMvc.perform(get("/billing/invoices"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "vendor@test.com")
    void getPaymentMethods_ShouldReturnPaymentMethodList() throws Exception {
        when(vendorContext.getCurrentVendorId()).thenReturn(vendorId);
        when(subscriptionService.getSubscriptionByVendorId(vendorId))
            .thenReturn(Optional.of(mockSubscription));

        mockMvc.perform(get("/billing/payment-methods"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "vendor@test.com")
    void addPaymentMethod_ShouldAttachPaymentMethod() throws Exception {
        when(vendorContext.getCurrentVendorId()).thenReturn(vendorId);
        when(subscriptionService.getSubscriptionByVendorId(vendorId))
            .thenReturn(Optional.of(mockSubscription));

        mockMvc.perform(post("/billing/payment-methods")
            .param("paymentMethodId", "pm_test123")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "vendor@test.com")
    void removePaymentMethod_ShouldDetachPaymentMethod() throws Exception {
        when(vendorContext.getCurrentVendorId()).thenReturn(vendorId);
        when(subscriptionService.getSubscriptionByVendorId(vendorId))
            .thenReturn(Optional.of(mockSubscription));

        mockMvc.perform(delete("/billing/payment-methods/pm_test123"))
            .andExpect(status().isNoContent());
    }

    @Test
    void getSubscriptionDetails_Unauthorized_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/billing/subscription"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getInvoices_Unauthorized_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/billing/invoices"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getPaymentMethods_Unauthorized_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/billing/payment-methods"))
            .andExpect(status().isUnauthorized());
    }
}
