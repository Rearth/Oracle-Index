package rearth.oracle.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import rearth.oracle.OracleClient;
import rearth.oracle.ui.OracleScreen;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
@Mixin(GuiGraphicsExtractor.class)
public class DrawContextMixin {
    
    @ModifyVariable(method = "setTooltipForNextFrameInternal", at = @At("HEAD"), argsOnly = true, name = "lines")
    private List<ClientTooltipComponent> injectTooltipComponents(List<ClientTooltipComponent> components) {
        if (OracleClient.tooltipStack == null) return components;
        if (Minecraft.getInstance().screen instanceof OracleScreen) return components;
        
        var stackItem = OracleClient.tooltipStack.getItem();
        var stackId = BuiltInRegistries.ITEM.getId(stackItem);
        
        if (!OracleClient.ITEM_LINKS.containsKey(stackId)) return components;
        
        OracleClient.tooltipStack = null;
        
        var modifiableComponents = new ArrayList<>(components);
        
        // vanilla-friendly separator: dim dashes line
        var separator = new ClientTextTooltip(Component.literal("─".repeat(20)).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        modifiableComponents.add(separator);
        
        var stackLink = OracleClient.ITEM_LINKS.get(stackId);
        
        Component icon = Component.literal("\uD83D\uDCD5 ").withStyle(ChatFormatting.GRAY);
        
        if (Minecraft.getInstance().hasAltDown()) {
            var dt = Minecraft.getInstance().getDeltaTracker().getRealtimeDeltaTicks() * .125f;
            OracleClient.openEntryProgress += (1.25f - OracleClient.openEntryProgress) * dt;
            var progressSteps = 40;
            var progress = (int) (OracleClient.openEntryProgress * progressSteps);
            progress = Math.clamp(progress, 0, 40);
            var missingSteps = progressSteps - progress;
            var progressText = "[" + "|".repeat(progress) + ".".repeat(missingSteps) + "]";
            var altTooltip = new ClientTextTooltip(icon.copy().append(Component.literal(progressText)).withStyle(ChatFormatting.GRAY).getVisualOrderText());
            modifiableComponents.add(altTooltip);
            
            if (OracleClient.openEntryProgress > 0.95f) {
                OracleClient.openScreen(stackLink.wikiId(), stackLink.linkTarget(), Minecraft.getInstance().screen);
            }
            
        } else {
            var tooltip = new ClientTextTooltip(icon.copy()
                .append(Component.translatable("oracle_index.tooltip.docs").withStyle(ChatFormatting.GRAY))
                .getVisualOrderText());
            modifiableComponents.add(tooltip);
        }
        
        return modifiableComponents;
    }
    
}
