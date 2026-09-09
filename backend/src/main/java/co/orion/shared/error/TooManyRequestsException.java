package co.orion.shared.error;

import java.time.Duration;

/** Se excedió un límite de intentos. Se traduce a 429 con la cabecera {@code Retry-After}. */
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
