package rearth.oracle_test;

import com.google.common.base.Suppliers;
import net.minecraft.item.Item;
import net.minecraft.item.Item.Settings;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

public class OracleTest {
    public static final String MODID = "oracle_index_test";
    
    public static final Supplier<Item> GREEN_APPLE = Suppliers.memoize(() -> new Item(new Settings()));
    public static final Supplier<Item> BLUE_APPLE = Suppliers.memoize(() -> new Item(new Settings()));
    public static final Supplier<Item> YELLOW_APPLE = Suppliers.memoize(() -> new Item(new Settings()));

    public static Identifier id(String path) {
        return Identifier.of(MODID, path);
    }
}
