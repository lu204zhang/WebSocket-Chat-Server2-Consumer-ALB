package vito.validator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import vito.model.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MessageValidator {

    private final Validator validator;

    /** @param validator Bean Validation validator instance */
    public MessageValidator(Validator validator) {
        this.validator = validator;
    }

    /**
     * Validates the chat message.
     * @param message message to validate
     * @return success or failure with error text
     */
    public ValidationResult validate(ChatMessage message) {
        Set<ConstraintViolation<ChatMessage>> violations = validator.validate(message);

        if (violations.isEmpty()) {
            return ValidationResult.success();
        }

        String errorMessage = violations.stream()
                .map(v -> String.format("Field '%s': %s", v.getPropertyPath().toString(), v.getMessage()))
                .collect(Collectors.joining("; "));

        return ValidationResult.failure(errorMessage);
    }
}
