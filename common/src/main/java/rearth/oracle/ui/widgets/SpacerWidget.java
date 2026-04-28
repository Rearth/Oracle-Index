package rearth.oracle.ui.widgets;

/**
 * Empty widget that occupies space. Useful inside flex containers.
 */
public class SpacerWidget extends UIComponent {
    
    public SpacerWidget(int width, int height) {
        size(width, height);
    }
    
    public static SpacerWidget vertical(int height) {
        return new SpacerWidget(0, height);
    }
    
    public static SpacerWidget horizontal(int width) {
        return new SpacerWidget(width, 0);
    }
    
    @Override
    protected void renderContent(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        // intentionally empty
    }
}
