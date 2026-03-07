package vito.queue;

public class CircuitOpenException extends RuntimeException {

    /** Thrown when circuit breaker is OPEN and publish is rejected. */
    public CircuitOpenException() {
        super("Circuit breaker is OPEN");
    }

    /** @param message custom error message */
    public CircuitOpenException(String message) {
        super(message);
    }
}
