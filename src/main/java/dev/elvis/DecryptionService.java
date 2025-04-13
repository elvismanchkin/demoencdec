package dev.elvis;

import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Singleton
public class DecryptionService {

    private static final Logger log = LoggerFactory.getLogger(DecryptionService.class);
    // Assume crypto helper configured internally

    /**
     * Decrypts the Base64 encoded data and deserializes the resulting JSON
     * into either the target success type or a specific ErrorDto.
     */
    public <T> Mono<DecryptionResult<T>> decryptAndDeserialize(
            String encryptedBase64Data,
            Class<T> successType,
            ObjectMapper specificMapper) {

        if (encryptedBase64Data == null || encryptedBase64Data.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Encrypted data cannot be null or empty"));
        }

        return Mono.fromCallable(() -> Base64.getDecoder().decode(encryptedBase64Data))
                .flatMap(this::performDecryption)
                .flatMap(decryptedBytes -> deserializePayload(decryptedBytes, successType, specificMapper))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Decryption and deserialization process failed", e))
                .onErrorMap(e -> !(e instanceof DeserializationException), e -> new RuntimeException("Decryption process failed", e));
    }

    /** Internal helper for the core crypto decryption */
    private Mono<byte[]> performDecryption(byte[] encryptedBytes) {
        return Mono.fromCallable(() -> {
                    // byte[] decryptedBytes = cryptoHelper.decryptBytes(encryptedBytes); // Actual decryption
                    String temp = new String(encryptedBytes, StandardCharsets.UTF_8); // Placeholder
                    if (!temp.startsWith("enc-")) {
                        throw new RuntimeException("Decryption failed (placeholder check)");
                    }
                    // Placeholder
                    return temp.substring(4).getBytes(StandardCharsets.UTF_8);
                })
                .onErrorMap(e -> new RuntimeException("Core decryption failed", e));
    }

    /** Internal helper to handle deserialization attempts */
    private <T> Mono<DecryptionResult<T>> deserializePayload(
            byte[] decryptedJsonBytes,
            Class<T> successType,
            ObjectMapper specificMapper) {

        return Mono.fromCallable(() -> {
            try { // Attempt 1: Success Type
                T successData = specificMapper.readValue(decryptedJsonBytes, successType);
                return new DecryptionResult.Success<>(successData);
            } catch (IOException e) {
                log.warn("Failed to deserialize as success type ({}), attempting error DTO. Reason: {}", successType.getName(), e.getMessage());
                try { // Attempt 2: Error DTO
                    ErrorDto errorData = specificMapper.readValue(decryptedJsonBytes, ErrorDto.class);
                    return new DecryptionResult.Error<>(errorData);
                } catch (IOException e2) {
                    log.error("Failed to deserialize decrypted data as success OR error type. Payload (limited): {}",
                            new String(decryptedJsonBytes, 0, Math.min(decryptedJsonBytes.length, 200), StandardCharsets.UTF_8), e2);
                    throw new DeserializationException("Cannot deserialize decrypted payload as "
                            + successType.getSimpleName() + " or " + ErrorDto.class.getSimpleName(), e2);
                }
            }
        });
    }
}