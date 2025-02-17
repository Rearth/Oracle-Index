package rearth.oracle.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import rearth.oracle.Oracle;

public final class OracleFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Oracle.init();
    }
}
