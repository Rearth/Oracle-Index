package rearth.oracle.util;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;

public class ContentProperties {
    
    public static LinkedHashMap<String, Component> getProperties(String ingameId) {
        
        var properties = new LinkedHashMap<String, Component>();
        var id = Identifier.parse(ingameId);
        
        
        // collect item properties
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            var item = BuiltInRegistries.ITEM.getValue(id);
            
            var defaultStack = new ItemStack(item);
            properties.put("Max Stack", Component.literal(String.valueOf(defaultStack.getMaxStackSize())));
            
            // Durability (1.21 Component System)
            var maxDamage = defaultStack.get(DataComponents.MAX_DAMAGE);
            if (maxDamage != null) {
                properties.put("Durability", Component.literal(String.valueOf(maxDamage)).withStyle(ChatFormatting.GREEN));
            }
            
        }
        
        // collect block properties
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            var block = BuiltInRegistries.BLOCK.getValue(id);
            var defaultState = block.defaultBlockState();
            if (defaultState.isAir()) return properties;
            
            properties.put("Hardness", Component.literal(String.valueOf(defaultState.getDestroySpeed(null, null))));
            properties.put("Resistance", Component.literal(String.valueOf(block.getExplosionResistance())));
            
            // Tool Requirement
            if (defaultState.requiresCorrectToolForDrops()) {
                properties.put("Tool Required", Component.literal("Yes").withStyle(ChatFormatting.RED));
            }
            
            // Determine Effective Tool via Tags
            var toolText = getEffectiveTool(defaultState);
            if (toolText != null) {
                properties.put("Effective Tool", toolText.withStyle(ChatFormatting.AQUA));
            }
            
            // Luminance
            int light = defaultState.getLightEmission();
            if (light > 0)
                properties.put("Light Level", Component.literal(String.valueOf(light)).withStyle(ChatFormatting.YELLOW));
            
        }
        
        return properties;
    }
    
    private static MutableComponent getEffectiveTool(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return Component.literal("Pickaxe");
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return Component.literal("Axe");
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return Component.literal("Shovel");
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return Component.literal("Hoe");
        return null;
    }
    
}
