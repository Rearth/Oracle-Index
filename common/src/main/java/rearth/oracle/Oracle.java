package rearth.oracle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Oracle {
    public static final String MOD_ID = "oracle_index";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        // Write common init code here.
        
        LOGGER.info("Hello from the Oracle Wiki Mod!");
        
    }
}
