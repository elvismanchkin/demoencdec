package dev.elvis;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CryptoWrapper(String data) {
}
