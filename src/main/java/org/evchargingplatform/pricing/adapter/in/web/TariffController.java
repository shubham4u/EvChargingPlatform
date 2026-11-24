package org.evchargingplatform.pricing.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.evchargingplatform.pricing.application.port.in.GetTariffUseCase;
import org.evchargingplatform.pricing.application.port.in.SetTariffCommand;
import org.evchargingplatform.pricing.application.port.in.SetTariffUseCase;
import org.evchargingplatform.pricing.domain.Tariff;
import org.evchargingplatform.shared.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * REST adapter for station tariffs. Reading is public, so drivers can see prices before plugging in;
 * setting is for operators (see SecurityConfig).
 */
@RestController
public class TariffController {

    private final SetTariffUseCase setTariffUseCase;
    private final GetTariffUseCase getTariffUseCase;

    public TariffController(SetTariffUseCase setTariffUseCase, GetTariffUseCase getTariffUseCase) {
        this.setTariffUseCase = setTariffUseCase;
        this.getTariffUseCase = getTariffUseCase;
    }

    public record TariffRequest(
            @NotBlank String currency,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal pricePerKwh,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal pricePerMinute,
            @NotNull @DecimalMin("0") @DecimalMax("1000") BigDecimal sessionFee) {
    }

    public record TariffResponse(UUID id, UUID stationId, String currency, BigDecimal pricePerKwh,
                                 BigDecimal pricePerMinute, BigDecimal sessionFee, Instant createdAt) {
        static TariffResponse from(Tariff t) {
            return new TariffResponse(t.id(), t.stationId(), t.currency(), t.pricePerKwh(), t.pricePerMinute(),
                    t.sessionFee(), t.createdAt());
        }
    }

    @PutMapping("/stations/{id}/tariff")
    public TariffResponse setTariff(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody TariffRequest request) {
        return TariffResponse.from(setTariffUseCase.setTariff(new SetTariffCommand(id, request.currency(),
                request.pricePerKwh(), request.pricePerMinute(), request.sessionFee(), AuthenticatedUser.from(jwt).id())));
    }

    @GetMapping("/stations/{id}/tariff")
    public TariffResponse currentTariff(@PathVariable UUID id) {
        return TariffResponse.from(getTariffUseCase.currentFor(id));
    }
}
