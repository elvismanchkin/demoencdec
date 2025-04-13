package dev.elvis;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Result wrapper for decryption operations, indicating either a successful deserialization
 * into the target type T or a structured business error represented by ErrorDto.
 * This interface itself MUST be public to be usable as a return type from public services.
 */
public sealed interface DecryptionResult<T> permits DecryptionResult.Success, DecryptionResult.Error {

    /** Represents a successful decryption and deserialization. */
    // Nested records/classes within a public interface are implicitly public
    @Serdeable
    record Success<T>(T data) implements DecryptionResult<T> {
    }

    /** Represents a successful decryption but the payload contained a known business error structure. */
    @Serdeable
    record Error<T>(ErrorDto errorDetails) implements DecryptionResult<T> {
    }

    // --- Convenience methods ---
    // Interface methods are public by default
    default boolean isSuccess() {
        return this instanceof Success;
    }

    default boolean isError() {
        return this instanceof Error;
    }

    default T getSuccessData() {
        if (this instanceof Success<T>(T data)) {
            return data;
        }
        throw new IllegalStateException("Result is not a Success: " + this);
    }

    default ErrorDto getErrorDetails() {
        if (this instanceof Error<T>(ErrorDto errorDetails)) {
            return errorDetails;
        }
        throw new IllegalStateException("Result is not an Error: " + this);
    }
}