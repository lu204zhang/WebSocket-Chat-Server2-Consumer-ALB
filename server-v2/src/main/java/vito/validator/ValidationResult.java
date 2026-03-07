package vito.validator;

public class ValidationResult {
    private final boolean valid;
    private final String message;

    private ValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    /** @return true if valid, false if validation failed */
    public boolean isValid() {
        return valid;
    }

    /** @return error message when validation failed; null when valid */
    public String getMessage() {
        return message;
    }

    /** @return result with valid true and null message */
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }

    /**
     * @param message error message for validation failure
     * @return result with valid false and the given message
     */
    public static ValidationResult failure(String message) {
        return new ValidationResult(false, message);
    }
}
