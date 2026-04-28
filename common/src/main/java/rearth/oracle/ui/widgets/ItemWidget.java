package rearth.oracle.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Renders an {@link ItemStack} (16×16). Optionally draws a {@link WikiSurface}
 * background behind the item — used for recipe slots so the slot frame and
 * item can overlap without two stacked containers.
 *
 * <p>When {@link #showTooltip} is true, hovering shows the item's vanilla tooltip.</p>
 */
public class ItemWidget extends UIComponent {
    
    private ItemStack stack;
    private boolean showTooltip = true;
    private boolean drawCount = true;
    private WikiSurface backgroundSurface = WikiSurface.NONE;
    private int backgroundPadding = 0;
    
    public ItemWidget(ItemStack stack) {
        this.stack = stack;
        size(16, 16);
    }
    
    public ItemWidget stack(ItemStack stack) { this.stack = stack; return this; }
    public ItemStack stack() { return stack; }
    
    public ItemWidget showTooltip(boolean show) { this.showTooltip = show; return this; }
    public ItemWidget drawCount(boolean drawCount) { this.drawCount = drawCount; return this; }
    
    /** Draw a background panel behind the item. {@code padding} extends the panel beyond the 16×16 item. */
    public ItemWidget background(WikiSurface surface, int padding) {
        this.backgroundSurface = surface;
        this.backgroundPadding = padding;
        return this;
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        if (backgroundSurface != WikiSurface.NONE) {
            backgroundSurface.render(context,
                x - backgroundPadding,
                y - backgroundPadding,
                width + backgroundPadding * 2,
                height + backgroundPadding * 2);
        }
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        var mc = MinecraftClient.getInstance();
        context.drawItem(stack, x, y);
        if (drawCount) context.drawItemInSlot(mc.textRenderer, stack, x, y);
    }
    
    @Override
    public List<Text> tooltip(int mouseX, int mouseY) {
        if (!showTooltip || stack == null || stack.isEmpty()) return super.tooltip(mouseX, mouseY);
        var mc = MinecraftClient.getInstance();
        return stack.getTooltip(net.minecraft.item.Item.TooltipContext.create(mc.world), mc.player,
            mc.options.advancedItemTooltips ? TooltipType.ADVANCED : TooltipType.BASIC);
    }
}
