package rearth.oracle.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
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
    
    /**
     * Optional dark fill behind everything. {@code 0} disables the fill.
     */
    protected int backgroundFillColor = 0xE6191923; // 90% alpha very dark blue-grey
    
    private final List<UIComponent> rootWidgets = new ArrayList<>();
    private boolean needsLayout = true;
    
    protected WikiBaseScreen(Component title) {
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
    
    /**
     * Subclasses lay out their root widgets here.
     */
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
    
    /**
     * Build the screen's widget tree. Called from {@link #init()}.
     */
    protected abstract void buildRoots();
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if ((backgroundFillColor & 0xFF000000) != 0) {
            context.fill(0, 0, this.width, this.height, backgroundFillColor);
        }
        if (needsLayout) {
            needsLayout = false;
            layoutWidgets();
        }
        // render vanilla drawable widgets (text fields etc) added via addDrawableChild
        super.extractRenderState(context, mouseX, mouseY, delta);
        for (var w : rootWidgets) w.render(context, mouseX, mouseY, delta);
        renderTooltip(context, mouseX, mouseY);
    }
    
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // suppress vanilla blur/dirt — we draw our own fill in render()
    }
    
    protected void renderTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        UIComponent hovered = topmostAt(mouseX, mouseY);
        if (hovered == null) return;
        var tip = hovered.tooltip(mouseX, mouseY);
        if (tip == null || tip.isEmpty()) return;
        context.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        for (int i = rootWidgets.size() - 1; i >= 0; i--) {
            var w = rootWidgets.get(i);
            if (w.isVisible() && w.handleClick(mouseX, mouseY, button)) {
                this.setFocused(null);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
    
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        boolean handled = false;
        for (var w : rootWidgets) if (w.isVisible() && w.handleMouseRelease(mouseX, mouseY, button)) handled = true;
        return handled || super.mouseReleased(event);
    }
    
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        for (int i = rootWidgets.size() - 1; i >= 0; i--) {
            var w = rootWidgets.get(i);
            if (w.isVisible() && w.handleDrag(mouseX, mouseY, dx, dy, button)) return true;
        }
        return super.mouseDragged(event, dx, dy);
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
    public boolean isPauseScreen() {
        return false;
    }
}
