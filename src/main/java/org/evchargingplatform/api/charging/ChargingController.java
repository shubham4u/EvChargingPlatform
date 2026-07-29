package org.evchargingplatform.api.charging;

import jakarta.validation.Valid;
import org.evchargingplatform.api.charging.dto.ChargingSessionResponse;
import org.evchargingplatform.api.charging.dto.StartChargingRequest;
import org.evchargingplatform.api.charging.dto.StopChargingRequest;
import org.evchargingplatform.domain.charging.ChargingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/charging")
public class ChargingController {
    private final ChargingService chargingService;

    public ChargingController(ChargingService chargingService) {
        this.chargingService = chargingService;
    }

    @PostMapping("/start")
    public ChargingSessionResponse start(@Valid @RequestBody StartChargingRequest request) {
        return chargingService.start(request);
    }

    @PostMapping("/stop")
    public ChargingSessionResponse stop(@Valid @RequestBody StopChargingRequest request) {
        return chargingService.stop(request);
    }
}

