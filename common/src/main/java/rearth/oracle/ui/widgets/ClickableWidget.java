package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

public class ClickableWidget extends UIComponent {
    
    private UIComponent child;
    private Consumer<ClickableWidget> onPress;
    private boolean enabled = true;
    private boolean selected = false;
    private boolean pressed = false;
    private boolean fillWidth = false;
    private boolean centerChild = false;
    private int fixedWidth = -1;
    private int fixedHeight = -1;
    
    private WikiSurface normalSurface = WikiSurface.NONE;
    private WikiSurface hoverSurface = WikiSurface.BEDROCK_PANEL_HOVER;
    private WikiSurface pressedSurface = WikiSurface.BEDROCK_PANEL_PRESSED;
    private WikiSurface selectedSurface = WikiSurface.BEDROCK_PANEL_DARK;
    private WikiSurface disabledSurface = WikiSurface.NONE;
    
    public ClickableWidget(UIComponent child, Consumer<ClickableWidget> onPress) {
        this.child = child;
        this.onPress = onPress;
    }
    
    public ClickableWidget enabled(boolean enabled) { this.enabled = enabled; return this; }
    
    public ClickableWidget selected(boolean selected) { this.selected = selected; return this; }
    
    public ClickableWidget fillWidth() { this.fillWidth = true; return this; }
    
    public ClickableWidget fixedHeight(int height) {
        this.fixedHeight = height;
        return this;
    }
    
    public ClickableWidget fixedSize(int width, int height) {
        this.fixedWidth = width;
        this.fixedHeight = height;
        return this;
    }
    
    public UIComponent getChild() {
        return child;
    }
    
    public ClickableWidget centerChild() {
        this.centerChild = true;
        return this;
    }
    
    public ClickableWidget surfaces(WikiSurface normal, WikiSurface hover, WikiSurface pressed, WikiSurface selected, WikiSurface disabled) {
        this.normalSurface = normal;
        this.hoverSurface = hover;
        this.pressedSurface = pressed;
        this.selectedSurface = selected;
        this.disabledSurface = disabled;
        return this;
    }
    
    @Override
    public int getPreferredWidth(int widthHint) {
        if (fixedWidth > 0) return fixedWidth;
        if (fillWidth && widthHint > 0) return widthHint;
        return child == null ? padding.horizontal() : child.getPreferredWidth(widthHint) + padding.horizontal();
    }
    
    @Override
    public int getPreferredHeight(int widthHint) {
        if (fixedHeight > 0) return fixedHeight;
        if (child == null) return padding.vertical();
        int innerHint = widthHint > 0 ? Math.max(1, widthHint - padding.horizontal()) : -1;
        return child.getPreferredHeight(innerHint) + padding.vertical();
    }
    
    @Override
    public void layout(int parentWidthHint, int parentHeightHint) {
        width = fixedWidth > 0 ? fixedWidth : parentWidthHint > 0 ? parentWidthHint : getPreferredWidth(-1);
        height = fixedHeight > 0 ? fixedHeight : getPreferredHeight(width);
        if (child == null) return;
        int innerW = Math.max(1, width - padding.horizontal());
        int innerH = Math.max(1, height - padding.vertical());
        int childW = innerW;
        int childH = innerH;
        if (centerChild) {
            childW = Math.min(innerW, child.getPreferredWidth(innerW));
            childH = Math.min(innerH, child.getPreferredHeight(childW));
        }
        child.setPosition(x + padding.left() + (centerChild ? (innerW - childW) / 2 : 0),
            y + padding.top() + (centerChild ? (innerH - childH) / 2 : 0));
        child.setLayoutSize(childW, childH);
        child.layout(childW, childH);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        var surface = currentSurface(mouseX, mouseY);
        if (!surface.isNone()) surface.render(context, x, y, width, height);
        if (child != null) child.render(context, mouseX, mouseY, delta);
    }
    
    public WikiSurface currentSurface(int mouseX, int mouseY) {
        if (!enabled) return disabledSurface;
        if (pressed) return pressedSurface;
        if (isInBounds(mouseX, mouseY) && !selected) return hoverSurface;
        return selected ? selectedSurface : normalSurface;
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {}
    
    @Override
    public void tick() {
        if (child != null) child.tick();
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
        return child != null && child.handleMouseRelease(mouseX, mouseY, button);
    }
    
    @Override
    public boolean handleMouseScroll(double mouseX, double mouseY, double scrollDelta) {
        return child != null && child.handleMouseScroll(mouseX, mouseY, scrollDelta);
    }
    
    @Override
    public List<Text> tooltip(int mouseX, int mouseY) {
        if (child != null && child.isInBounds(mouseX, mouseY)) {
            var tip = child.tooltip(mouseX, mouseY);
            if (tip != null && !tip.isEmpty()) return tip;
        }
        return super.tooltip(mouseX, mouseY);
    }
}