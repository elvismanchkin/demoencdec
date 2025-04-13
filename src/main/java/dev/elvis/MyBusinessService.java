package dev.elvis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@Singleton
public class MyBusinessService {

    private static final Logger log = LoggerFactory.getLogger(MyBusinessService.class);

    @Inject
    MyExternalServiceClient externalClient;
    @Inject DecryptionService decryptionService;
    @Inject EncryptionService encryptionService;
    ObjectMapper targetMapper;


    public Mono<SomeSpecificObject> getDecryptedData(String id) {
        log.info("Fetching encrypted data for id: {}", id);

        return externalClient.getEncryptedResource(id)
                .onErrorResume(HttpClientResponseException.class, e -> {
                    int statusCode = e.getStatus().getCode();
                    String responseBody = e.getResponse().getBody(String.class).orElse("<no body>");
                    log.error("HTTP error {} from external service for id {}. Body: {}", statusCode, id, responseBody, e);
                    // Convert to a specific application exception for HTTP errors
                    return Mono.error(new HttpApiException("External service request failed", statusCode, responseBody, e));
                })
                // ** Proceed with decryption IF HTTP call was successful **
                .flatMap(wrapper -> {
                    if (wrapper == null || wrapper.data() == null || wrapper.data().isEmpty()) {
                        log.warn("Received empty wrapper or data for id: {}", id);
                        return Mono.error(new RuntimeException("Received no encrypted data from client for id " + id));
                    }
                    return decryptionService.decryptAndDeserialize(
                            wrapper.data(),
                            SomeSpecificObject.class,
                            targetMapper
                    );
                })
                // ** Handle DecryptionResult (Success or embedded ErrorDto) **
                .flatMap(result -> switch (result) {
                    case DecryptionResult.Success<SomeSpecificObject> success -> Mono.just(success.data());
                    case DecryptionResult.Error<SomeSpecificObject> error -> {
                        log.warn("Decryption resulted in a known API error for id {}: {}", id, error.errorDetails());
                        yield Mono.error(new ApiException(error.errorDetails())); // Throw specific exception
                    }
                })
                // ** Handle specific application/decryption errors **
                .onErrorResume(ApiException.class, Mono::error) // Let specific API errors pass through
                .onErrorResume(HttpApiException.class, Mono::error) // Let specific HTTP errors pass through
                .onErrorResume(DeserializationException.class, e -> {
                    log.error("Failed to deserialize decrypted payload for id {}: {}", id, e.getMessage());
                    return Mono.error(new RuntimeException("Malformed data from external service for id " + id, e));
                })
                // ** Catch-all for any other unexpected errors **
                .onErrorResume(e -> {
                    // Avoid wrapping errors we already handled
                    if (e instanceof ApiException || e instanceof HttpApiException || e instanceof DeserializationException) {
                        return Mono.error(e);
                    }
                    log.error("Unexpected error processing data for id {}: {}", id, e.getMessage(), e);
                    return Mono.error(new RuntimeException("Operation failed unexpectedly for id " + id, e));
                });
    }

    public Mono<Void> sendEncryptedData(SomeSpecificObject dataToSend) {
        return encryptionService.serializeAndEncrypt(dataToSend, targetMapper)
                .map(CryptoWrapper::new)
                .flatMap(externalClient::postEncryptedResource);
    }
}

