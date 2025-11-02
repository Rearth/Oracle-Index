package rearth.oracle.progress;

import rearth.oracle.Oracle;

import java.util.HashMap;
import java.util.Map;

/**
 * API for managing and validating progress unlocks for Oracle Wiki entries.
 * Allows registration of custom unlock validators and checking unlock status.
 */
public class OracleProgressAPI {

    private static final Map<String, ProgressValidator> REGISTERED_VALIDATORS = new HashMap<>();
    
    public static boolean IsUnlocked(String bookId, String entryId, String validatorType, String validatorTarget) {

        if (REGISTERED_VALIDATORS.containsKey(validatorType)) {
            var validator = REGISTERED_VALIDATORS.get(validatorType);
            return validator.validate(bookId, entryId, validatorTarget);
        } else {
            Oracle.LOGGER.warn("tried to validate page requirement for unregistered validator: {}", validatorType);
        }

        return false;
    }
    public static void RegisterValidator(ProgressValidator validator, String validatorId) {
        REGISTERED_VALIDATORS.put(validatorId, validator);
    }
    
    @FunctionalInterface
    public interface ProgressValidator {
        
        boolean validate(String bookId, String entryId, String validatorTarget);
        
    }

}