package rearth.oracle.ui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Renders an {@link ItemStack} and exposes its vanilla tooltip on hover.
 */
public class ItemWidget extends UIComponent {
    
    private final ItemStack stack;
    private TooltipMode tooltipMode;
    private boolean hideItemDecorations;
    
    public ItemWidget(ItemStack stack) {
        this.stack = stack;
        size(16, 16);
    }
    
    @Override
    protected void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        var mc = Minecraft.getInstance();
        int itemSize = Math.max(1, Math.min(width, height));
        float scale = itemSize / 16f;
        int drawX = x + (width - itemSize) / 2;
        int drawY = y + (height - itemSize) / 2;
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(drawX, drawY);
        matrices.scale(scale, scale);
        context.item(stack, 0, 0);
        if (!hideItemDecorations) context.itemDecorations(mc.font, stack, 0, 0);
        matrices.popMatrix();
    }

    public void setTooltipMode(TooltipMode tooltipMode) {
        this.tooltipMode = tooltipMode;
    }

    public void setHideItemDecorations(boolean hideItemDecorations) {
        this.hideItemDecorations = hideItemDecorations;
    }
    
    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        if (stack == null || stack.isEmpty() || tooltipMode == TooltipMode.HIDDEN) return super.tooltip(mouseX, mouseY);
        var mc = Minecraft.getInstance();
        List<Component> tooltip = stack.getTooltipLines(
            Item.TooltipContext.of(mc.level),
            mc.player,
            mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL
        );
        return tooltipMode == TooltipMode.NAME_ONLY && !tooltip.isEmpty() ? tooltip.subList(0, 1) : tooltip;
    }
}
