package rearth.oracle.util;

import dev.architectury.registry.fuel.FuelRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;

public class ContentProperties {
    
    public static LinkedHashMap<String, Text> getProperties(String ingameId) {
        
        var properties = new LinkedHashMap<String, Text>();
        var id = Identifier.of(ingameId);
        
        
        // collect item properties
        if (Registries.ITEM.containsId(id)) {
            var item = Registries.ITEM.get(id);
            
            properties.put(label("max_stack"), Text.literal(String.valueOf(item.getMaxCount())));
            
            // Durability (1.21 Component System)
            var maxDamage = item.getComponents().get(DataComponentTypes.MAX_DAMAGE);
            if (maxDamage != null) {
                properties.put(label("durability"), Text.literal(String.valueOf(maxDamage)).formatted(Formatting.GREEN));
            }
            
            // Fuel Value
            int fuel = FuelRegistry.get(new ItemStack(item));
            if (fuel > 0) {
                properties.put(label("fuel_value"), Text.literal(fuel + " ticks").formatted(Formatting.GOLD));
            }
        }
        
        // collect block properties
        if (Registries.BLOCK.containsId(id)) {
            var block = Registries.BLOCK.get(id);
            var defaultState = block.getDefaultState();
            if (defaultState.isAir()) return properties;
            
            properties.put(label("hardness"), Text.literal(String.valueOf(defaultState.getHardness(null, null))));
            properties.put(label("resistance"), Text.literal(String.valueOf(block.getBlastResistance())));
            
            // Tool Requirement
            if (defaultState.isToolRequired()) {
                properties.put(label("tool_required"), Text.translatable("oracle_index.value.yes").formatted(Formatting.RED));
            }
            
            // Determine Effective Tool via Tags
            var toolText = getEffectiveTool(defaultState);
            if (toolText != null) {
                properties.put(label("effective_tool"), toolText.formatted(Formatting.AQUA));
            }
            
            // Luminance
            int light = defaultState.getLuminance();
            if (light > 0)
                properties.put(label("light_level"), Text.literal(String.valueOf(light)).formatted(Formatting.YELLOW));
            
        }
        
        return properties;
    }
    
    private static MutableText getEffectiveTool(BlockState state) {
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) return Text.translatable("oracle_index.tool.pickaxe");
        if (state.isIn(BlockTags.AXE_MINEABLE)) return Text.translatable("oracle_index.tool.axe");
        if (state.isIn(BlockTags.SHOVEL_MINEABLE)) return Text.translatable("oracle_index.tool.shovel");
        if (state.isIn(BlockTags.HOE_MINEABLE)) return Text.translatable("oracle_index.tool.hoe");
        return null;
    }

    private static String label(String name) {
        return "oracle_index.property." + name;
    }
    
}
