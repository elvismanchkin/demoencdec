package dev.elvis;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record ErrorDto(
        String errorCode,
        String message,
        Map<String, Object> details
) {
}
