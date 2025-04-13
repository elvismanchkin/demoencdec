package dev.elvis;

import reactor.core.publisher.Mono;

public interface MyExternalServiceClient {
    Mono<CryptoWrapper> getEncryptedResource(String id);

    Mono<Void> postEncryptedResource(CryptoWrapper encryptedPayload);
}
