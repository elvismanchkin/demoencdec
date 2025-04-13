package dev.elvis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MicronautTest(environments = "mock-rabbitmq") // Load the specific test environment config

//@MicronautTest // Loads Micronaut context, enables DI and mocking
class MyRpcListenerTest {

    @Inject
    MyRpcListener listener; // Inject the bean under test

    // Inject the *real* named ObjectMapper bean we want to verify is used for replies
    @Inject
    @Named("externalServiceObjectMapper")
    ObjectMapper externalServiceObjectMapper;

    // Inject the ApplicationContext to potentially grab beans if needed (optional)
    @Inject
    ApplicationContext applicationContext;

    // Mock the EncryptionService - we don't want real encryption,
    // just want to verify what object and mapper it's called with.
    @Inject
    EncryptionService encryptionServiceMock;

    // Factory method needed by @MockBean to create the mock
    @MockBean(EncryptionService.class)
    EncryptionService encryptionService() {
        return mock(EncryptionService.class);
    }

    // Capture arguments passed to the mocked service
    ArgumentCaptor<Object> objectArgumentCaptor;
    ArgumentCaptor<ObjectMapper> mapperArgumentCaptor;

    @BeforeEach
    void setUp() {
        // Reset mock before each test (good practice)
        reset(encryptionServiceMock);

        // Setup argument captors
        objectArgumentCaptor = ArgumentCaptor.forClass(Object.class);
        mapperArgumentCaptor = ArgumentCaptor.forClass(ObjectMapper.class);

        // Define mock behavior: when serializeAndEncrypt is called, capture args and return dummy success
        when(encryptionServiceMock.serializeAndEncrypt(objectArgumentCaptor.capture(), mapperArgumentCaptor.capture()))
                .thenReturn(Mono.just("dummy-encrypted-base64-string"));
    }

    @Test
    @DisplayName("Should process request, decode field, and call EncryptionService with correct object and CAMEL_CASE mapper")
    void testProcessRpcRequest_SuccessFlow() throws Exception {
        // --- Arrange ---

        // 1. Input object (as if deserialized by Micronaut's default SNAKE_CASE mapper)
        String originalSensitiveData = "Decoded Value";
        // Example assumes Base64 encoding was used for the field
        String encodedSensitiveData = Base64.getEncoder().encodeToString(originalSensitiveData.getBytes(StandardCharsets.UTF_8));
        SomeSpecificObject inputRequestData = new SomeSpecificObject(
                "id-123",
                "Test Value",
                99,
                encodedSensitiveData // Field is encoded here
        );

        // 2. Mock AMQP properties
        String expectedCorrelationId = "corr-abc";
        AMQP.BasicProperties mockProperties = new AMQP.BasicProperties.Builder()
                .correlationId(expectedCorrelationId)
                .replyTo("test-reply-queue")
                .build();

        // --- Act ---
        // Call the listener method directly
        CryptoWrapper result = listener.processRpcRequestAutoDeserializeSnake(inputRequestData, mockProperties).block(); // Using block() for simplicity in test

        // --- Assert ---

        // 1. Verify EncryptionService was called exactly once
        verify(encryptionServiceMock, times(1)).serializeAndEncrypt(any(), any(ObjectMapper.class));

        // 2. Get captured arguments
        Object capturedObject = objectArgumentCaptor.getValue();
        ObjectMapper capturedMapper = mapperArgumentCaptor.getValue();

        // 3. Verify the object passed to encryption service
        assertNotNull(capturedObject, "Object passed to encryption service should not be null");
        assertInstanceOf(SomeSpecificObject.class, capturedObject, "Object should be of type SomeSpecificObject");
        SomeSpecificObject objectToEncrypt = (SomeSpecificObject) capturedObject;

        // Check that the sensitive data field was DECODED before encryption
        assertEquals(originalSensitiveData, objectToEncrypt.sensitiveData(), "Sensitive data should be decoded");
        // Check other fields are passed correctly
        assertEquals(inputRequestData.id(), objectToEncrypt.id());
        assertEquals(inputRequestData.value(), objectToEncrypt.value());
        assertEquals(inputRequestData.count(), objectToEncrypt.count());

        // 4. Verify the ObjectMapper instance passed to encryption service
        assertNotNull(capturedMapper, "ObjectMapper passed to encryption service should not be null");
        // Check if it's the exact bean instance we injected (verifies @Named injection worked correctly)
        assertSame(externalServiceObjectMapper, capturedMapper, "Should use the @Named('externalServiceObjectMapper') bean");

        // 5. Verify the configuration of the captured mapper (indirectly)
        // Serialize the captured object *using the captured mapper* to check naming strategy
        String jsonOutput = capturedMapper.writeValueAsString(objectToEncrypt);
        assertTrue(jsonOutput.contains("\"id\":\"id-123\""), "JSON output should use CAMEL_CASE keys (id)");
        assertTrue(jsonOutput.contains("\"value\":\"Test Value\""), "JSON output should use CAMEL_CASE keys (value)");
        assertTrue(jsonOutput.contains("\"count\":99"), "JSON output should use CAMEL_CASE keys (count)");
        assertTrue(jsonOutput.contains("\"sensitiveData\":\"" + originalSensitiveData + "\""), "JSON output should use CAMEL_CASE keys (sensitiveData)");
        assertFalse(jsonOutput.contains("\"sensitive_data\""), "JSON output should NOT use SNAKE_CASE keys");

        // 6. Verify the listener's return value matches the mock's return
        assertNotNull(result);
        assertEquals("dummy-encrypted-base64-string", result.data(), "Listener should return the encrypted string from the service");
    }

    @Test
    @DisplayName("Should return empty Mono if decoding fails")
    void testProcessRpcRequest_DecodingFailure() {
        // --- Arrange ---
        SomeSpecificObject inputRequestData = new SomeSpecificObject(
                "id-fail", "Fail Value", 1, "this-is-not-valid-base64" // Invalid encoded data
        );
        AMQP.BasicProperties mockProperties = new AMQP.BasicProperties.Builder().correlationId("corr-fail").build();

        // --- Act ---
        Mono<CryptoWrapper> resultMono = listener.processRpcRequestAutoDeserializeSnake(inputRequestData, mockProperties);

        // --- Assert ---
        // Verify the Mono completes empty (no reply sent)
        assertNull(resultMono.block(), "Mono should complete empty on decoding failure");
        // Verify encryption service was NOT called
        verify(encryptionServiceMock, never()).serializeAndEncrypt(any(), any());
    }

    @Test
    @DisplayName("Should return empty Mono if input data is null")
    void testProcessRpcRequest_NullInput() {
        // --- Arrange ---
        AMQP.BasicProperties mockProperties = new AMQP.BasicProperties.Builder().correlationId("corr-null").build();

        // --- Act ---
        Mono<CryptoWrapper> resultMono = listener.processRpcRequestAutoDeserializeSnake(null, mockProperties); // Pass null input

        // --- Assert ---
        assertNull(resultMono.block(), "Mono should complete empty on null input");
        verify(encryptionServiceMock, never()).serializeAndEncrypt(any(), any());
    }
}