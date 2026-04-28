package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every wiki UI widget.
 *
 * <p>Each widget owns an absolute position + size, an optional {@link WikiSurface}
 * background drawn underneath the content, padding that expands the surface
 * outward, a tooltip, and a z-index.
 *
 * <p>Containers are responsible for laying out their children and may call
 * {@link #layout(int, int)} on each child. Leaf widgets only need to override
 * {@link #renderContent(DrawContext, int, int, float)}.
 */
public abstract class UIComponent {
    
    protected int x, y, width, height;
    protected Insets padding = Insets.NONE;
    protected WikiSurface surface = WikiSurface.NONE;
    protected int zIndex = 0;
    protected boolean visible = true;
    
    private List<Text> tooltip;
    
    /** Set by the screen / parent container so widgets can request a re-layout. */
    @Nullable private Runnable layoutRequester;
    
    public UIComponent() {
    }
    
    public UIComponent(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    /** Fluent setter for size. */
    public UIComponent size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }
    
    /** Fluent setter for position. */
    public UIComponent position(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }
    
    /** Alias for {@link #isMouseOver(double, double)}. */
    public boolean isInBounds(double mouseX, double mouseY) {
        return isMouseOver(mouseX, mouseY);
    }
    
    /** Tooltip lookup; subclasses can return a position-dependent tooltip. */
    public List<Text> tooltip(int mouseX, int mouseY) {
        return tooltip;
    }
    
    // --------------------------------------------------------------- rendering
    
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        
        if (!surface.isNone()) {
            surface.render(context,
              x - padding.left(), y - padding.top(),
              width + padding.horizontal(), height + padding.vertical());
        }
        
        renderContent(context, mouseX, mouseY, delta);
    }
    
    protected abstract void renderContent(DrawContext context, int mouseX, int mouseY, float delta);
    
    public void tick() {}
    
    // --------------------------------------------------------------- layout
    
    /**
     * Resolves this widget's size and the size + position of its children given
     * the parent's available space. Default does nothing — subclasses with a
     * dynamic size (containers, wrapped labels) override.
     *
     * <p>Width / height hints of -1 mean "no constraint, use intrinsic size".
     */
    public void layout(int parentWidthHint, int parentHeightHint) {}
    
    /**
     * Preferred width given a width hint (-1 = no hint).
     * Default returns the current explicit width.
     */
    public int getPreferredWidth(int widthHint) {
        return width;
    }
    
    /** Preferred height. Width hint already resolved. -1 = no constraint. */
    public int getPreferredHeight(int widthHint) {
        return height;
    }
    
    /**
     * Asks the screen / nearest container to re-run layout next frame.
     * No-op when the widget is not yet attached.
     */
    public void requestLayout() {
        if (layoutRequester != null) layoutRequester.run();
    }
    
    public void setLayoutRequester(@Nullable Runnable layoutRequester) {
        this.layoutRequester = layoutRequester;
    }
    
    // --------------------------------------------------------------- mouse
    
    public boolean isMouseOver(double mouseX, double mouseY) {
        int px = x - padding.left();
        int py = y - padding.top();
        int pw = width + padding.horizontal();
        int ph = height + padding.vertical();
        return mouseX >= px && mouseX < px + pw && mouseY >= py && mouseY < py + ph;
    }
    
    public boolean handleClick(double mouseX, double mouseY, int button) { return false; }
    public boolean handleDrag(double mouseX, double mouseY, double deltaX, double deltaY, int button) { return false; }
    public boolean handleMouseRelease(double mouseX, double mouseY, int button) { return false; }
    public boolean handleMouseScroll(double mouseX, double mouseY, double scrollDelta) { return false; }
    
    // --------------------------------------------------------------- accessors
    
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Insets getPadding() { return padding; }
    public WikiSurface getSurface() { return surface; }
    public int getZIndex() { return zIndex; }
    public boolean isVisible() { return visible; }
    
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public void setSize(int width, int height) { this.width = width; this.height = height; }
    public void setPadding(Insets padding) { this.padding = padding; }
    public void setSurface(WikiSurface surface) { this.surface = surface; }
    public void setZIndex(int zIndex) { this.zIndex = zIndex; }
    public void setVisible(boolean visible) { this.visible = visible; }
    
    // Content-area helpers (currently identical to widget bounds; padding sits outside)
    public int contentX() { return x; }
    public int contentY() { return y; }
    public int contentWidth() { return width; }
    public int contentHeight() { return height; }
    
    public int paddedX() { return x - padding.left(); }
    public int paddedY() { return y - padding.top(); }
    public int paddedWidth() { return width + padding.horizontal(); }
    public int paddedHeight() { return height + padding.vertical(); }
    
    // --------------------------------------------------------------- fluent
    
    public UIComponent withSurface(WikiSurface surface) { this.surface = surface; return this; }
    public UIComponent withPadding(Insets padding) { this.padding = padding; return this; }
    public UIComponent withZIndex(int zIndex) { this.zIndex = zIndex; return this; }
    public UIComponent withVisible(boolean visible) { this.visible = visible; return this; }
    
    public UIComponent withTooltip(Text... lines) {
        this.tooltip = splitNewlines(List.of(lines));
        return this;
    }
    
    public UIComponent withTooltip(List<Text> lines) {
        this.tooltip = splitNewlines(lines);
        return this;
    }
    
    public boolean hasTooltip() { return tooltip != null && !tooltip.isEmpty(); }
    public List<Text> getTooltip() { return tooltip; }
    public void setTooltip(@Nullable List<Text> tooltip) { this.tooltip = tooltip != null ? splitNewlines(tooltip) : null; }
    
    private static List<Text> splitNewlines(List<Text> lines) {
        var result = new ArrayList<Text>();
        for (var line : lines) {
            var str = line.getString();
            if (str.contains("\n")) {
                for (var part : str.split("\n", -1)) result.add(Text.literal(part));
            } else {
                result.add(line);
            }
        }
        return result;
    }
}
