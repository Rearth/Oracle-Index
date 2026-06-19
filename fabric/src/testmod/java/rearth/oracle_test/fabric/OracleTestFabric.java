package rearth.oracle_test.fabric;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import rearth.oracle_test.OracleTest;

public class OracleTestFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, OracleTest.id("green_apple"), OracleTest.GREEN_APPLE.get());
        Registry.register(Registries.ITEM, OracleTest.id("blue_apple"), OracleTest.BLUE_APPLE.get());
        Registry.register(Registries.ITEM, OracleTest.id("yellow_apple"), OracleTest.YELLOW_APPLE.get());
    }
}
