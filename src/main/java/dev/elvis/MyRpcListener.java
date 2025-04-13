package dev.elvis;

import com.rabbitmq.client.AMQP.BasicProperties;
import io.micronaut.rabbitmq.annotation.Queue;
import io.micronaut.rabbitmq.annotation.RabbitListener;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@RabbitListener // Indicates this class contains listener methods
public class MyRpcListener {

    private static final Logger log = LoggerFactory.getLogger(MyRpcListener.class);

    @Inject
    MyBusinessLogicService businessLogic;
    @Inject
    EncryptionService encryptionService;

    // Inject the ObjectMapper configured for the client that will DECRYPT this response
    @Inject
    @Named("clientFacingMapper")
    ObjectMapper clientResponseMapper;

    // Optional: Inject acknowledger for manual ACKs if needed
    // @Inject RabbitAcknowledgement acknowledger;

    /**
     * Listens for RPC requests, performs business logic, encrypts the result,
     * and returns it wrapped in Mono for automatic reply.
     *
     * @param requestId      The request body (deserialized by Micronaut, here assumed String).
     * @param properties     AMQP message properties (contains replyTo, correlationId).
     * @return A Mono emitting the CryptoWrapper containing the encrypted response.
     * If the Mono completes empty or errors, typically no reply is sent (client times out).
     */
    @Queue("rpc.request.queue") // Listen on this specific queue
    // Default acknowledgment is AUTO. Use MANUAL if needed with RabbitAcknowledgement.
    public Mono<CryptoWrapper> processRpcRequest(String requestId, BasicProperties properties) {

        final String correlationId = properties != null ? properties.getCorrelationId() : "[unknown]";
        log.info("RPC Request received. ID: '{}', CorrelationId: '{}'", requestId, correlationId);

        // 1. Execute business logic (returns Mono<SomeSpecificObject>)
        return businessLogic.getDataForId(requestId) // Assuming this returns Mono<SomeSpecificObject>
                .flatMap(responseObject -> {
                    // 2. Serialize and Encrypt the successful response object
                    log.debug("Encrypting successful response for CorrelationId: '{}'", correlationId);
                    return encryptionService.serializeAndEncrypt(responseObject, clientResponseMapper);
                })
                .map(encryptedString -> {
                    // 3. Wrap the encrypted string in the CryptoWrapper DTO
                    log.debug("Wrapping encrypted response for CorrelationId: '{}'", correlationId);
                    return new CryptoWrapper(encryptedString);
                    // This CryptoWrapper will be serialized (e.g., to JSON) and sent as the reply body
                })
                .doOnSuccess(wrapper -> log.info("Successfully processed and encrypted response for CorrelationId: '{}'", correlationId))
                // 4. Handle errors DURING processing/encryption
                .onErrorResume(error -> {
                    log.error("Error processing request for CorrelationId: '{}'. Error: {}", correlationId, error.getMessage(), error);

                    // === Error Handling Strategy ===
                    // Decide how to respond when your internal processing fails.

                    // Option A: Send nothing back (Client times out) - Common AMQP RPC pattern
                    return Mono.empty(); // Explicitly signals no reply should be sent

                    // Option B: Attempt to encrypt and send a structured error DTO
                    // ErrorDto errorDto = new ErrorDto("SERVER_ERROR", "Failed to process request: " + error.getMessage(), Collections.emptyMap());
                    // return encryptionService.serializeAndEncrypt(errorDto, clientResponseMapper)
                    //         .map(CryptoWrapper::new)
                    //         .doOnError(encErr -> log.error("Failed even to encrypt the error DTO for CorrelationId: '{}'", correlationId, encErr))
                    //         .onErrorResume(encErr -> Mono.empty()); // Fallback to sending nothing if error encryption fails

                    // Option C: Let error propagate (Listener might Nack message depending on config, likely no reply)
                    // return Mono.error(error);
                });
        // The final Mono<CryptoWrapper> (or Mono.empty()/Mono.error() from onErrorResume) is returned.
        // Micronaut RabbitMQ handles subscribing and sending the emitted item (if any) as the reply.
    }
}

// --- Dummy Business Logic Service for context ---
@Singleton
class MyBusinessLogicService {
    public Mono<SomeSpecificObject> getDataForId(String id) {
        // Simulate async database lookup or other logic
        if ("error".equals(id)) {
            return Mono.error(new RuntimeException("Simulated business logic error for id: " + id));
        }
        return Mono.just(new SomeSpecificObject(id, "Data for " + id, 123));
    }
}

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