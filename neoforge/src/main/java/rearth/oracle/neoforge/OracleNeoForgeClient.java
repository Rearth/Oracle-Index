package rearth.oracle.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

import rearth.oracle.Oracle;
import rearth.oracle.OracleClient;

@Mod(value = Oracle.MOD_ID, dist = Dist.CLIENT)
public final class OracleNeoForgeClient {
    public OracleNeoForgeClient() {
        // Run our common setup.
        OracleClient.init();
    }
}
