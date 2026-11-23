package org.evchargingplatform.payments.adapter.in.web;

import org.evchargingplatform.payments.application.port.in.GetPaymentUseCase;
import org.evchargingplatform.payments.application.port.in.ManagePaymentMethodUseCase;
import org.evchargingplatform.payments.application.port.in.RefundPaymentUseCase;
import org.evchargingplatform.payments.domain.PaymentIntent;
import org.evchargingplatform.payments.domain.PaymentMethod;
import org.evchargingplatform.shared.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.evchargingplatform.support.TestJwt.driver;
import static org.evchargingplatform.support.TestJwt.operator;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link PaymentController}.
 */
@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Clock CLOCK = Clock.systemUTC();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManagePaymentMethodUseCase paymentMethodUseCase;
    @MockBean
    private GetPaymentUseCase getPaymentUseCase;
    @MockBean
    private RefundPaymentUseCase refundPaymentUseCase;

    private final PaymentMethod card = new PaymentMethod(USER, "tok_visa", "visa", "4242", CLOCK.instant());
    private final PaymentIntent captured = PaymentIntent.authorized(UUID.randomUUID(), USER, "GBP", new BigDecimal("50.00"),
            card, "sim_auth", CLOCK).captured(new BigDecimal("21.24"), CLOCK);

    @Test
    @DisplayName("PUT /payments/methods/me saves the caller's card and never echoes the token")
    void shouldSaveCard() throws Exception {
        when(paymentMethodUseCase.setPaymentMethod(USER, "tok_visa")).thenReturn(card);

        mockMvc.perform(put("/payments/methods/me").with(driver(USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"tok_visa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("visa"))
                .andExpect(jsonPath("$.last4").value("4242"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("an unknown card token is 400, and no card yet is 404")
    void shouldExplainCardProblems() throws Exception {
        when(paymentMethodUseCase.setPaymentMethod(USER, "4242424242424242"))
                .thenThrow(new IllegalArgumentException("Unknown card token"));
        when(paymentMethodUseCase.findPaymentMethod(USER)).thenReturn(Optional.empty());

        mockMvc.perform(put("/payments/methods/me").with(driver(USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"4242424242424242\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/payments/methods/me").with(driver(USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment not found"));
    }

    @Test
    @DisplayName("drivers list their own payments; operators list all; the card token is not exposed")
    void shouldScopeList() throws Exception {
        when(getPaymentUseCase.findByUser(USER)).thenReturn(List.of(captured));
        when(getPaymentUseCase.findAll()).thenReturn(List.of(captured, captured));

        mockMvc.perform(get("/payments").with(driver(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CAPTURED"))
                .andExpect(jsonPath("$[0].capturedAmount").value(21.24))
                .andExpect(jsonPath("$[0].cardToken").doesNotExist());
        mockMvc.perform(get("/payments").with(operator(UUID.randomUUID())))
                .andExpect(jsonPath("$.length()").value(2));
        verify(getPaymentUseCase, times(1)).findAll();
    }

    @Test
    @DisplayName("GET /payments/{id} is 403 for another driver")
    void shouldGuardRead() throws Exception {
        when(getPaymentUseCase.findById(captured.id())).thenReturn(captured);

        mockMvc.perform(get("/payments/{id}", captured.id()).with(driver(USER))).andExpect(status().isOk());
        mockMvc.perform(get("/payments/{id}", captured.id()).with(driver(UUID.randomUUID()))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("refund is operator-only, and 409 for a payment that can't be refunded")
    void shouldGuardRefund() throws Exception {
        UUID uncaptured = UUID.randomUUID();
        when(refundPaymentUseCase.refund(captured.id())).thenReturn(captured.refunded(CLOCK));
        when(refundPaymentUseCase.refund(uncaptured)).thenThrow(new IllegalStateException("Can only refund a payment that is CAPTURED"));

        mockMvc.perform(post("/payments/{id}/refund", captured.id()).with(driver(USER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/payments/{id}/refund", captured.id()).with(operator(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mockMvc.perform(post("/payments/{id}/refund", uncaptured).with(operator(UUID.randomUUID())))
                .andExpect(status().isConflict());
        verify(refundPaymentUseCase, times(2)).refund(any());   // the driver's attempt never reached the use case
    }
}
