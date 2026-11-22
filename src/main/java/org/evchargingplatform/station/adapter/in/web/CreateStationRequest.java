package org.evchargingplatform.station.adapter.in.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateStationRequest(
        @NotBlank @Size(max = 100) String externalReference,
        @NotBlank @Size(max = 200) String name,
        @NotNull @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude) {
}
