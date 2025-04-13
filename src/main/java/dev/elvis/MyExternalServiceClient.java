package dev.elvis;

import reactor.core.publisher.Mono;

interface MyExternalServiceClient {
    Mono<CryptoWrapper> getEncryptedResource(String id);

    Mono<Void> postEncryptedResource(CryptoWrapper encryptedPayload);
}
