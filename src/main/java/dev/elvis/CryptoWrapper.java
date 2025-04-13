package dev.elvis;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record CryptoWrapper(String data) {
}
