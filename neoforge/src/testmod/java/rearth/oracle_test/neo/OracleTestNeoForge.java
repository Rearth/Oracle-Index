package rearth.oracle_test.neo;

import net.minecraft.registry.RegistryKeys;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;
import rearth.oracle_test.OracleTest;

@Mod(OracleTest.MODID)
public class OracleTestNeoForge {

    public OracleTestNeoForge(IEventBus bus) {
        bus.addListener(RegisterEvent.class, e -> {
            e.register(RegistryKeys.ITEM, OracleTest.id("green_apple"), OracleTest.GREEN_APPLE);
            e.register(RegistryKeys.ITEM, OracleTest.id("blue_apple"), OracleTest.BLUE_APPLE);
            e.register(RegistryKeys.ITEM, OracleTest.id("yellow_apple"), OracleTest.YELLOW_APPLE);
        });
    }
}
