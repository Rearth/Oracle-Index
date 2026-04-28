package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import rearth.oracle.Oracle;

/**
 * Wraps an {@link ItemWidget} in the standard 18×18 wiki slot frame
 * ({@code oracle:textures/gui/item_cell.png}).
 */
public class ItemSlotWidget extends UIComponent {
    
    public static final Identifier SLOT_TEXTURE = Identifier.of(Oracle.MOD_ID, "textures/item_cell.png");
    public static final int SLOT_SIZE = 18;
    
    private final ItemWidget item;
    
    public ItemSlotWidget(ItemWidget item) {
        this.item = item;
        size(SLOT_SIZE, SLOT_SIZE);
    }
    
    public ItemWidget item() {
        return item;
    }
    
    @Override
    public void layout(int parentWidthHint, int parentHeightHint) {
        // place the item centered (16x16 inside an 18x18 slot)
        item.setPosition(x + 1, y + 1);
        item.setLayoutSize(16, 16);
        item.layout(16, 16);
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawTexture(SLOT_TEXTURE, x, y, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
        item.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public java.util.List<net.minecraft.text.Text> tooltip(int mouseX, int mouseY) {
        if (item.isInBounds(mouseX, mouseY)) {
            var t = item.tooltip(mouseX, mouseY);
            if (t != null) return t;
        }
        return super.tooltip(mouseX, mouseY);
    }
    
    @Override
    public boolean handleClick(double mouseX, double mouseY, int button) {
        return item.handleClick(mouseX, mouseY, button);
    }
}
