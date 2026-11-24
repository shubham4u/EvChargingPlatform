package org.evchargingplatform.payments.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.evchargingplatform.payments.application.port.in.GetPaymentUseCase;
import org.evchargingplatform.payments.application.port.in.ManagePaymentMethodUseCase;
import org.evchargingplatform.payments.application.port.in.RefundPaymentUseCase;
import org.evchargingplatform.payments.domain.PaymentIntent;
import org.evchargingplatform.payments.domain.PaymentMethod;
import org.evchargingplatform.payments.domain.exception.PaymentNotFoundException;
import org.evchargingplatform.shared.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST adapter for payments: your saved card, your payment history, and operator refunds
 * (operator-only, see SecurityConfig).
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final ManagePaymentMethodUseCase paymentMethodUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    private final RefundPaymentUseCase refundPaymentUseCase;

    public PaymentController(ManagePaymentMethodUseCase paymentMethodUseCase, GetPaymentUseCase getPaymentUseCase,
                             RefundPaymentUseCase refundPaymentUseCase) {
        this.paymentMethodUseCase = paymentMethodUseCase;
        this.getPaymentUseCase = getPaymentUseCase;
        this.refundPaymentUseCase = refundPaymentUseCase;
    }

    public record PaymentMethodRequest(@NotBlank String token) {
    }

    public record PaymentMethodResponse(String brand, String last4, Instant updatedAt) {
        static PaymentMethodResponse from(PaymentMethod m) {
            return new PaymentMethodResponse(m.brand(), m.last4(), m.updatedAt());
        }
    }

    // The card token is deliberately not returned.
    public record PaymentResponse(UUID id, UUID sessionId, UUID userId, String status, String currency,
                                  BigDecimal authorizedAmount, BigDecimal capturedAmount, String cardBrand,
                                  String cardLast4, String failureCode, String failureReason,
                                  Instant createdAt, Instant updatedAt) {
        static PaymentResponse from(PaymentIntent p) {
            return new PaymentResponse(p.id(), p.sessionId(), p.userId(), p.status().name(), p.currency(),
                    p.authorizedAmount(), p.capturedAmount(), p.cardBrand(), p.cardLast4(), p.failureCode(),
                    p.failureReason(), p.createdAt(), p.updatedAt());
        }
    }

    @PutMapping("/methods/me")
    public PaymentMethodResponse setPaymentMethod(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody PaymentMethodRequest request) {
        return PaymentMethodResponse.from(paymentMethodUseCase.setPaymentMethod(AuthenticatedUser.from(jwt).id(), request.token()));
    }

    @GetMapping("/methods/me")
    public PaymentMethodResponse paymentMethod(@AuthenticationPrincipal Jwt jwt) {
        return paymentMethodUseCase.findPaymentMethod(AuthenticatedUser.from(jwt).id())
                .map(PaymentMethodResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException("You have no saved card; add one with PUT /payments/methods/me"));
    }

    @GetMapping
    public List<PaymentResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser caller = AuthenticatedUser.from(jwt);
        List<PaymentIntent> payments = caller.operator() ? getPaymentUseCase.findAll() : getPaymentUseCase.findByUser(caller.id());
        return payments.stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PaymentResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        PaymentIntent payment = getPaymentUseCase.findById(id);
        AuthenticatedUser.from(jwt).requireOwnerOrOperator(payment.userId());
        return PaymentResponse.from(payment);
    }

    // Operator only (see SecurityConfig)
    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable UUID id) {
        return PaymentResponse.from(refundPaymentUseCase.refund(id));
    }
}
