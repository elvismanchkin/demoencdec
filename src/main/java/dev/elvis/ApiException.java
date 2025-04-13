package dev.elvis;

class ApiException extends RuntimeException {
    private final ErrorDto errorDetails;
    public ApiException(ErrorDto errorDetails) {
        super(errorDetails.message() != null ? errorDetails.message() : "API Error");
        this.errorDetails = errorDetails;
    }
    public ErrorDto getErrorDetails() { return errorDetails; }
}