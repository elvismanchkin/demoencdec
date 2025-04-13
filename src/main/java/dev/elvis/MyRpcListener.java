package dev.elvis;

// Assuming your business logic is now *triggered* by the listener, but the data is already present

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP.BasicProperties;
import io.micronaut.rabbitmq.annotation.Queue;
import io.micronaut.rabbitmq.annotation.RabbitListener;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Singleton
@RabbitListener
public class MyRpcListener {
    private static final Logger log = LoggerFactory.getLogger(MyRpcListener.class);

    @Inject EncryptionService encryptionService;

    // Inject the NAMED ObjectMapper for CAMEL_CASE replies
    @Inject @Named("externalServiceObjectMapper")
    ObjectMapper externalServiceObjectMapper;


    @Queue("rpc.request.queue.v3") // Your queue name
    public Mono<CryptoWrapper> processRpcRequestAutoDeserializeSnake(
            SomeSpecificObject requestData, // Micronaut attempts deserialization using default (SNAKE_CASE)
            BasicProperties properties) {

        final String correlationId = properties != null ? properties.getCorrelationId() : "[unknown]";
        log.info("RPC Request object received (Auto-Deserialized with default SNAKE_CASE mapper). Type: '{}', CorrelationId: '{}'",
                requestData != null ? requestData.getClass().getSimpleName() : "null", correlationId);

        // Micronaut's listener error handling might catch deserialization errors before this point,
        // but a null check is still good practice.
        if (requestData == null) {
            log.error("Received null request data object after deserialization. CorrelationId: '{}'", correlationId);
            return Mono.empty(); // Or other error handling
        }

        // 1. Decode specific field
        SomeSpecificObject dataToEncrypt;
        try {
            String decodedSensitiveData = decodeSensitiveDataField(requestData.sensitiveData());
            // Create new object with decoded data
            dataToEncrypt = new SomeSpecificObject(
                    requestData.id(), requestData.value(), requestData.count(), decodedSensitiveData
            );
            log.debug("Decoded sensitive field for CorrelationId: {}", correlationId);
        } catch (Exception e) {
            log.error("Failed decoding field. CorrId: '{}'. Error: {}", correlationId, e.getMessage(), e);
            return Mono.empty(); // Or other error handling
        }

        // 2. Encrypt reply using EncryptionService, passing the EXTERNAL (CAMEL_CASE) mapper
        log.debug("Encrypting response object using externalServiceObjectMapper (CAMEL_CASE) for CorrelationId: '{}'", correlationId);
        return encryptionService.serializeAndEncrypt(dataToEncrypt, externalServiceObjectMapper) // Pass the specific CAMEL_CASE mapper
                .map(CryptoWrapper::new)
                .doOnSuccess(wrapper -> log.info("Processed and encrypted response for CorrelationId: '{}'", correlationId))
                .onErrorResume(error -> {
                    log.error("Error during encryption/wrapping stage for CorrelationId: '{}'. Error: {}",
                            correlationId, error.getMessage(), error);
                    return Mono.empty();
                });
    }

    // Example decoding helper
    private String decodeSensitiveDataField(String encodedData) {
        if (encodedData == null) return null;
        try {
            // Replace with your actual decoding logic
            log.trace("Decoding sensitive data field (assuming Base64)...");
            byte[] decodedBytes = Base64.getDecoder().decode(encodedData);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid encoding for sensitive data field", e);
        }
    }
}


// Remember the named ObjectMapper bean factory and Encryption/Decryption services

// --- Remember to have a named ObjectMapper bean factory ---
/*
@Factory
class ObjectMapperFactory {
    @Singleton @Named("clientFacingMapper")
    public ObjectMapper clientFacingMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Configure this mapper based on what the RPC client expects
        return mapper;
    }
}
*/