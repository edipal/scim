package com.scimplayground.validator.mgmt.dto;

import com.scimplayground.validator.mgmt.model.ValidationHttpExchange;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ValidationHttpExchangeView(
    UUID id,
    int sequenceNumber,
    String method,
    String url,
    String requestHeaders,
    String requestBody,
    Integer responseStatus,
    String responseHeaders,
    String responseBody,
    OffsetDateTime createdAt
) {
    public String getDisplayUrl() {
        if (url == null || url.isBlank()) {
            return url;
        }
        try {
            return URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return url;
        }
    }

    public static ValidationHttpExchangeView from(ValidationHttpExchange exchange) {
        return new ValidationHttpExchangeView(
            exchange.getId(),
            exchange.getSequenceNumber(),
            exchange.getMethod(),
            exchange.getUrl(),
            exchange.getRequestHeaders(),
            exchange.getRequestBody(),
            exchange.getResponseStatus(),
            exchange.getResponseHeaders(),
            exchange.getResponseBody(),
            exchange.getCreatedAt()
        );
    }
}
