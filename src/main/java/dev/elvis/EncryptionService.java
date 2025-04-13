package dev.elvis;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Singleton
public class EncryptionService {
    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);
    // No mapper injected here, it must be provided by the caller

    /**
     * Serializes object using the *provided* ObjectMapper and encrypts.
     * @param plainObject Object to serialize/encrypt.
     * @param specificMapper ObjectMapper to use for serialization (e.g., CAMEL_CASE one).
     * @return Mono emitting Base64 encoded encrypted string.
     */
    public Mono<String> serializeAndEncrypt(Object plainObject, ObjectMapper specificMapper) {
        if (plainObject == null) return Mono.error(new IllegalArgumentException("Cannot encrypt null object"));
        if (specificMapper == null) return Mono.error(new IllegalArgumentException("ObjectMapper cannot be null"));

        return Mono.fromCallable(() -> {
                    // Use the mapper passed by the caller
                    byte[] jsonBytes = specificMapper.writeValueAsBytes(plainObject);
                    log.debug("Serialized {} bytes using provided ObjectMapper: {}", jsonBytes.length, specificMapper.getClass().getSimpleName());
                    /* === Placeholder: Replace with actual encryption logic === */
                    byte[] encryptedBytes = ("enc-" + new String(jsonBytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                    /* === End Placeholder === */
                    return Base64.getEncoder().encodeToString(encryptedBytes);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Encryption process failed", e))
                .onErrorMap(e -> new RuntimeException("Encryption process failed", e));
    }
}