package org.evchargingplatform.api.sessions;

import org.evchargingplatform.api.charging.dto.ChargingSessionResponse;
import org.evchargingplatform.domain.charging.ChargingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/sessions")
public class SessionController {
    private final ChargingService chargingService;

    public SessionController(ChargingService chargingService) {
        this.chargingService = chargingService;
    }

    @GetMapping("/{id}")
    public ChargingSessionResponse getById(@PathVariable UUID id) {
        return chargingService.getById(id);
    }
}

