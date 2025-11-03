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

    /**
     * Checks if a specific entry in a book is unlocked based on the provided validator type and target.
     *
     * @param bookId         The ID of the book containing the entry.
     * @param entryId        The path of the entry to check (e.g. `developer/setup.mdx`).
     * @param validatorType  The type of validator to use for the check (e.g. `advancement`).
     * @param validatorTarget The target data for the validator (e.g. ``minecraft:oritech/pulverizer).
     * @return True if the entry is unlocked, false otherwise.
     */
    public static boolean IsUnlocked(String bookId, String entryId, String validatorType, String validatorTarget) {
    
        if (REGISTERED_VALIDATORS.containsKey(validatorType)) {
            var validator = REGISTERED_VALIDATORS.get(validatorType);
            return validator.validate(bookId, entryId, validatorTarget);
        } else {
            Oracle.LOGGER.warn("tried to validate page requirement for unregistered validator: {}", validatorType);
        }
    
        return false;
    }
    
    /**
     * Registers a new progress validator with a unique identifier.
     *
     * @param validator   The validator to register.
     * @param validatorId The unique identifier for the validator.
     */
    public static void RegisterValidator(ProgressValidator validator, String validatorId) {
        REGISTERED_VALIDATORS.put(validatorId, validator);
    }
    
    @FunctionalInterface
    public interface ProgressValidator {
        
        boolean validate(String bookId, String entryId, String validatorTarget);
        
    }

}