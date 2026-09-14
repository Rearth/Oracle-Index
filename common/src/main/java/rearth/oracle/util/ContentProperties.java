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
            properties.put(label("max_stack"), Component.literal(String.valueOf(defaultStack.getMaxStackSize())));
            
            // Durability (1.21 Component System)
            var maxDamage = defaultStack.get(DataComponents.MAX_DAMAGE);
            if (maxDamage != null) {
                properties.put(label("durability"), Component.literal(String.valueOf(maxDamage)).withStyle(ChatFormatting.GREEN));
            }
            
        }
        
        // collect block properties
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            var block = BuiltInRegistries.BLOCK.getValue(id);
            var defaultState = block.defaultBlockState();
            if (defaultState.isAir()) return properties;
            
            properties.put(label("hardness"), Component.literal(String.valueOf(defaultState.getDestroySpeed(null, null))));
            properties.put(label("resistance"), Component.literal(String.valueOf(block.getExplosionResistance())));
            
            // Tool Requirement
            if (defaultState.requiresCorrectToolForDrops()) {
                properties.put(label("tool_required"), Component.translatable("oracle_index.value.yes").withStyle(ChatFormatting.RED));
            }
            
            // Determine Effective Tool via Tags
            var toolText = getEffectiveTool(defaultState);
            if (toolText != null) {
                properties.put(label("effective_tool"), toolText.withStyle(ChatFormatting.AQUA));
            }
            
            // Luminance
            int light = defaultState.getLightEmission();
            if (light > 0)
                properties.put(label("light_level"), Component.literal(String.valueOf(light)).withStyle(ChatFormatting.YELLOW));
            
        }
        
        return properties;
    }
    
    private static MutableComponent getEffectiveTool(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return Component.translatable("oracle_index.tool.pickaxe");
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return Component.translatable("oracle_index.tool.axe");
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return Component.translatable("oracle_index.tool.shovel");
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return Component.translatable("oracle_index.tool.hoe");
        return null;
    }

    private static String label(String name) {
        return "oracle_index.property." + name;
    }
    
}
