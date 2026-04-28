package rearth.oracle.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Renders an {@link ItemStack} and exposes its vanilla tooltip on hover.
 */
public class ItemWidget extends UIComponent {
    
    private ItemStack stack;
    
    public ItemWidget(ItemStack stack) {
        this.stack = stack;
        size(16, 16);
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        var mc = MinecraftClient.getInstance();
        int itemSize = Math.max(1, Math.min(width, height));
        float scale = itemSize / 16f;
        int drawX = x + (width - itemSize) / 2;
        int drawY = y + (height - itemSize) / 2;
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(drawX, drawY, 0);
        matrices.scale(scale, scale, 1f);
        context.drawItem(stack, 0, 0);
        context.drawItemInSlot(mc.textRenderer, stack, 0, 0);
        matrices.pop();
    }
    
    @Override
    public List<Text> tooltip(int mouseX, int mouseY) {
        if (stack == null || stack.isEmpty()) return super.tooltip(mouseX, mouseY);
        var mc = MinecraftClient.getInstance();
        return stack.getTooltip(net.minecraft.item.Item.TooltipContext.create(mc.world), mc.player,
            mc.options.advancedItemTooltips ? TooltipType.ADVANCED : TooltipType.BASIC);
    }
}
