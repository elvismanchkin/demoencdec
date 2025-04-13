package dev.elvis;

public class HttpApiException extends RuntimeException {
    private final int statusCode;
    private final String responseBody; // Include response body if available/readable
    public HttpApiException(String message, int statusCode, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
    public int getStatusCode() { return statusCode; }
    public String getResponseBody() { return responseBody; }
}