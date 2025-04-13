package dev.elvis;

import io.micronaut.serde.ObjectMapper;
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
    // Assume crypto helper configured internally

    /**
     * Serializes the object using the provided mapper, encrypts the resulting JSON bytes,
     * and returns a Base64 encoded string.
     */
    public Mono<String> serializeAndEncrypt(Object plainObject, ObjectMapper specificMapper) {
        if (plainObject == null) {
            return Mono.error(new IllegalArgumentException("Input object cannot be null"));
        }
        return Mono.fromCallable(() -> {
                    byte[] jsonBytes = specificMapper.writeValueAsBytes(plainObject);
                    // byte[] encryptedBytes = cryptoHelper.encryptBytes(jsonBytes); // Actual encryption
                    byte[] encryptedBytes = ("enc-" + new String(jsonBytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8); // Placeholder
                    return Base64.getEncoder().encodeToString(encryptedBytes);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Encryption process failed", e))
                .onErrorMap(e -> new RuntimeException("Encryption process failed", e));
    }
}