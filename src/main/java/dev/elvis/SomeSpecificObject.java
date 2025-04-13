package dev.elvis;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SomeSpecificObject(
        String id,
        String value,
        int count,
        String sensitiveData
) {}