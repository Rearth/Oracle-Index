package rearth.oracle.neoforge;

import net.neoforged.fml.common.Mod;
import rearth.oracle.Oracle;

@Mod(Oracle.MOD_ID)
public final class OracleNeoForge {
    public OracleNeoForge() {
        // Run our common setup.
        Oracle.init();
    }
}
