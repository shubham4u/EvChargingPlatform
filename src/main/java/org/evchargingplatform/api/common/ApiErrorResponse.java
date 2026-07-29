package org.evchargingplatform.api.common;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        Instant timestamp,
        Map<String, Object> errors
) {
}

