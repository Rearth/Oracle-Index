package rearth.oracle.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import rearth.oracle.ui.widgets.UIComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Common scaffolding for wiki screens. Owns a list of root widgets, runs
 * layout when invalidated, dispatches mouse / scroll / tick events to them
 * and renders tooltips.
 */
public abstract class WikiBaseScreen extends Screen {
    
    /** Optional dark fill behind everything. {@code 0} disables the fill. */
    protected int backgroundFillColor = 0xE6191923; // 90% alpha very dark blue-grey
    
    private final List<UIComponent> rootWidgets = new ArrayList<>();
    private boolean needsLayout = true;
    
    protected WikiBaseScreen(Text title) {
        super(title);
    }
    
    protected void addRoot(UIComponent widget) {
        widget.setLayoutRequester(this::requestLayout);
        rootWidgets.add(widget);
        needsLayout = true;
    }
    
    public void requestLayout() {
        needsLayout = true;
    }
    
    /** Subclasses lay out their root widgets here. */
    protected abstract void layoutWidgets();
    
    @Override
    protected void init() {
        super.init();
        // give subclasses a chance to (re)create their tree on init
        rootWidgets.forEach(w -> w.setLayoutRequester(null));
        rootWidgets.clear();
        buildRoots();
        needsLayout = true;
    }
    
    /** Build the screen's widget tree. Called from {@link #init()}. */
    protected abstract void buildRoots();
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if ((backgroundFillColor & 0xFF000000) != 0) {
            context.fill(0, 0, this.width, this.height, backgroundFillColor);
        }
        if (needsLayout) {
            needsLayout = false;
            layoutWidgets();
        }
        // render vanilla drawable widgets (text fields etc) added via addDrawableChild
        super.render(context, mouseX, mouseY, delta);
        for (var w : rootWidgets) w.render(context, mouseX, mouseY, delta);
        renderTooltip(context, mouseX, mouseY);
    }
    
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // suppress vanilla blur/dirt — we draw our own fill in render()
    }
    
    protected void renderTooltip(DrawContext context, int mouseX, int mouseY) {
        UIComponent hovered = topmostAt(mouseX, mouseY);
        if (hovered == null) return;
        var tip = hovered.tooltip(mouseX, mouseY);
        if (tip == null || tip.isEmpty()) return;
        context.drawTooltip(MinecraftClient.getInstance().textRenderer, tip, mouseX, mouseY);
    }
    
    @Nullable
    private UIComponent topmostAt(double mouseX, double mouseY) {
        for (int i = rootWidgets.size() - 1; i >= 0; i--) {
            var w = rootWidgets.get(i);
            if (w.isVisible() && w.isInBounds(mouseX, mouseY)) return w;
        }
        return null;
    }
    
    @Override
    public void tick() {
        super.tick();
        for (var w : rootWidgets) w.tick();
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = rootWidgets.size() - 1; i >= 0; i--) {
            var w = rootWidgets.get(i);
            if (w.isVisible() && w.handleClick(mouseX, mouseY, button)) {
                this.setFocused(null);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        for (var w : rootWidgets) if (w.isVisible() && w.handleMouseRelease(mouseX, mouseY, button)) handled = true;
        return handled || super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        for (int i = rootWidgets.size() - 1; i >= 0; i--) {
            var w = rootWidgets.get(i);
            if (w.isVisible() && w.handleDrag(mouseX, mouseY, dx, dy, button)) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (int i = rootWidgets.size() - 1; i >= 0; i--) {
            var w = rootWidgets.get(i);
            if (w.isVisible() && w.handleMouseScroll(mouseX, mouseY, verticalAmount)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public boolean shouldPause() { return false; }
}
