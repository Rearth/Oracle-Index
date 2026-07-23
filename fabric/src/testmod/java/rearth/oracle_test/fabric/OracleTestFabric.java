package rearth.oracle_test.fabric;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import rearth.oracle_test.OracleTest;

public class OracleTestFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.ITEM, OracleTest.id("green_apple"), OracleTest.GREEN_APPLE.get());
        Registry.register(BuiltInRegistries.ITEM, OracleTest.id("blue_apple"), OracleTest.BLUE_APPLE.get());
        Registry.register(BuiltInRegistries.ITEM, OracleTest.id("yellow_apple"), OracleTest.YELLOW_APPLE.get());
    }
}
