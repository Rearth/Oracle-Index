package rearth.oracle_test;

import com.google.common.base.Suppliers;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

public class OracleTest {
    public static final String MODID = "oracle_index_test";
    
    public static final Supplier<Item> GREEN_APPLE = item("green_apple");
    public static final Supplier<Item> BLUE_APPLE = item("blue_apple");
    public static final Supplier<Item> YELLOW_APPLE = item("yellow_apple");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private static Supplier<Item> item(String path) {
        return Suppliers.memoize(() -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(path)))));
    }
}
