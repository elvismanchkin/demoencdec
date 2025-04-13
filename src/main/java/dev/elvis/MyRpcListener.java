package dev.elvis;

// Assuming your business logic is now *triggered* by the listener, but the data is already present

import com.rabbitmq.client.AMQP.BasicProperties;
import io.micronaut.rabbitmq.annotation.Queue;
import io.micronaut.rabbitmq.annotation.RabbitListener;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@RabbitListener
public class MyRpcListener {

    private static final Logger log = LoggerFactory.getLogger(MyRpcListener.class);

    // Inject services needed for processing/encryption
    @Inject
    EncryptionService encryptionService;
    @Inject
    @Named("clientFacingMapper")
    ObjectMapper clientResponseMapper;
    // Potentially other services if further processing of requestData is needed

    /**
     * Listens for RPC requests where the body is the data object itself.
     * Processes the data (if needed), encrypts it, and returns the encrypted reply.
     *
     * @param requestData    The request body, deserialized into SomeSpecificObject by Micronaut.
     * @param properties     AMQP message properties (contains replyTo, correlationId).
     * @return A Mono emitting the CryptoWrapper containing the encrypted response.
     */
    @Queue("rpc.request.queue.object") // Use the correct queue name
    public Mono<CryptoWrapper> processRpcRequestWithObject(
            SomeSpecificObject requestData, // Micronaut deserializes the incoming body to this
            BasicProperties properties) {

        final String correlationId = properties != null ? properties.getCorrelationId() : "[unknown]";
        log.info("RPC Request object received. Type: '{}', CorrelationId: '{}'",
                requestData != null ? requestData.getClass().getSimpleName() : "null", correlationId);

        if (requestData == null) {
            log.error("Received null request data object for CorrelationId: '{}'", correlationId);
            // Handle null input - maybe return an encrypted error? Or empty.
            return Mono.empty(); // Example: Send no reply
        }

        // Optional: Perform any synchronous or asynchronous processing on requestData if needed
        // If processing returns a Mono, you would use flatMap here.
        // Mono<SomeSpecificObject> processedDataMono = someProcessingService.process(requestData);
        // return processedDataMono.flatMap(processedData -> { ... encryption logic ... });

        // --- If no further async processing of requestData is needed ---

        // 1. Directly call the encryption service with the received object.
        //    This returns Mono<String>.
        log.debug("Encrypting received object for CorrelationId: '{}'", correlationId);
        return encryptionService.serializeAndEncrypt(requestData, clientResponseMapper)
                .map(encryptedString -> {
                    // 2. Map the resulting encrypted string (emitted by the Mono) to the wrapper DTO.
                    log.debug("Wrapping encrypted response for CorrelationId: '{}'", correlationId);
                    return new CryptoWrapper(encryptedString);
                })
                .doOnSuccess(wrapper -> log.info("Successfully processed and encrypted response for CorrelationId: '{}'", correlationId))
                // 3. Handle errors during the encryption/serialization process
                .onErrorResume(error -> {
                    log.error("Error processing/encrypting request for CorrelationId: '{}'. Error: {}",
                            correlationId, error.getMessage(), error);
                    // Error handling strategy (e.g., return Mono.empty() to avoid reply)
                    return Mono.empty();
                });
        // The final Mono<CryptoWrapper> is returned. Micronaut handles the reply.
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