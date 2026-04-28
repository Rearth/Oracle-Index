package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;

/**
 * Solid filled rectangle. Honours {@link #surface} for the background and then
 * fills its inner area with {@link #color} (ARGB).
 */
public class BoxWidget extends UIComponent {
    
    private int color = 0xFFFFFFFF;
    
    public BoxWidget(int width, int height) {
        size(width, height);
    }
    
    public BoxWidget color(int argb) {
        this.color = argb;
        return this;
    }
    
    public int color() {
        return color;
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(x, y, x + width, y + height, color);
    }
}
