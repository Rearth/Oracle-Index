package rearth.oracle.progress;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import rearth.oracle.Oracle;

public class AdvancementProgressValidator {
    
    private static final OracleProgressAPI.ProgressValidator VALIDATOR = (bookId, entryId, validatorTarget) -> {
        
        var player = MinecraftClient.getInstance().player;
        if (player == null) {
            Oracle.LOGGER.warn("Advancement progress validation failed: player is null");
            return false;
        }
        
        var id = Identifier.of(validatorTarget);
        var advancementHandler = player.networkHandler.getAdvancementHandler();
        if (advancementHandler == null) {
            Oracle.LOGGER.warn("Advancement progress validation failed: player's advancement handler is null");
            return false;
        }
        
        var advancementEntry = advancementHandler.get(id);
        
        if (advancementEntry == null) { // not even in preview stage yet
            return false;
        }
        
        var advancementProgress = advancementHandler.advancementProgresses.get(advancementEntry);
        
        return advancementProgress != null && advancementProgress.isDone();
    };
    
    public static void register() {
        OracleProgressAPI.RegisterValidator(VALIDATOR, "advancement");
    }
    
}
