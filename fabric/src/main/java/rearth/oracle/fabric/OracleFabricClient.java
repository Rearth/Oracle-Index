package rearth.oracle.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import rearth.oracle.Oracle;
import rearth.oracle.OracleClient;

public final class OracleFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OracleClient.init();
    }
}
