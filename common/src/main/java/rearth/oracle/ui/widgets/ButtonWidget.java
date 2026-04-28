package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Clickable button. Uses {@link WikiSurface} for normal/hover/pressed/disabled
 * backgrounds and renders a centered {@link Text} label.
 *
 * <p>Custom rendering can be supplied via {@link #renderer(ButtonRenderer)},
 * which fully replaces the default surface+label render. This is used by the
 * action-hub icon buttons (back, search, close).</p>
 */
public class ButtonWidget extends UIComponent {
    
    @FunctionalInterface
    public interface ButtonRenderer {
        void render(ButtonWidget button, DrawContext context, int mouseX, int mouseY, float delta);
    }
    
    private Text label;
    private int color = 0xFFFFFFFF;
    private boolean enabled = true;
    private boolean pressed = false;
    private Consumer<ButtonWidget> onPress;
    
    private WikiSurface normalSurface = WikiSurface.BEDROCK_PANEL;
    private WikiSurface hoverSurface = WikiSurface.BEDROCK_PANEL_HOVER;
    private WikiSurface pressedSurface = WikiSurface.BEDROCK_PANEL_PRESSED;
    private WikiSurface disabledSurface = WikiSurface.BEDROCK_PANEL_DISABLED;
    
    private ButtonRenderer renderer;
    
    public ButtonWidget(Text label, Consumer<ButtonWidget> onPress) {
        this.label = label;
        this.onPress = onPress;
    }
    
    public ButtonWidget label(Text label) { this.label = label; return this; }
    public Text label() { return label; }
    
    public ButtonWidget color(int argb) { this.color = argb; return this; }
    
    public ButtonWidget enabled(boolean enabled) { this.enabled = enabled; return this; }
    public boolean enabled() { return enabled; }
    
    public ButtonWidget onPress(Consumer<ButtonWidget> onPress) { this.onPress = onPress; return this; }
    
    public ButtonWidget surfaces(WikiSurface normal, WikiSurface hover, WikiSurface pressed, WikiSurface disabled) {
        this.normalSurface = normal;
        this.hoverSurface = hover;
        this.pressedSurface = pressed;
        this.disabledSurface = disabled;
        return this;
    }
    
    public ButtonWidget renderer(ButtonRenderer renderer) { this.renderer = renderer; return this; }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        if (renderer != null) {
            renderer.render(this, context, mouseX, mouseY, delta);
            return;
        }
        // pick the right state surface, override the configured surface field
        var prev = surface;
        surface = currentStateSurface(mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        surface = prev;
    }
    
    private WikiSurface currentStateSurface(int mouseX, int mouseY) {
        if (!enabled) return disabledSurface;
        if (pressed) return pressedSurface;
        if (isInBounds(mouseX, mouseY)) return hoverSurface;
        return normalSurface;
    }
    
    public boolean isHovered(int mouseX, int mouseY) {
        return isInBounds(mouseX, mouseY);
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        if (label == null) return;
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        var tr = mc.textRenderer;
        int textW = tr.getWidth(label);
        int textX = x + (width - textW) / 2;
        int textY = y + (height - tr.fontHeight) / 2 + 1;
        context.drawText(tr, label, textX, textY, enabled ? color : 0xFF888888, false);
    }
    
    @Override
    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (!enabled || button != 0 || !isInBounds(mouseX, mouseY)) return false;
        pressed = true;
        if (onPress != null) onPress.accept(this);
        return true;
    }
    
    @Override
    public boolean handleMouseRelease(double mouseX, double mouseY, int button) {
        if (button == 0 && pressed) {
            pressed = false;
            return true;
        }
        return false;
    }
}
