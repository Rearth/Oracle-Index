package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Vertical scroll viewport. Wraps a single child widget; if the child is
 * taller than this widget's {@link #height}, the user can scroll it.
 *
 * <p>Clipping uses {@link DrawContext#enableScissor(int, int, int, int)} for
 * a sharp viewport, then a soft 6-px fade gradient at the top/bottom edge
 * indicates more content is available.</p>
 *
 * <p>The scroll bar appears only while hovered or while a scroll is in
 * progress, then fades out after {@link #SCROLL_BAR_VISIBLE_TICKS}.</p>
 */
public class ScrollWidget extends UIComponent {
    
    private static final int FADE_HEIGHT = 6;
    private static final int SCROLL_BAR_WIDTH = 4;
    private static final int SCROLL_BAR_VISIBLE_TICKS = 25; // ~1.25s
    private static final float SCROLL_LERP = 0.15f;
    
    private UIComponent child;
    private float scrollOffset = 0;
    private int targetScrollOffset = 0;
    private int contentHeight = 0;
    private int scrollBarVisibleFrames = 0;
    private int scrollSpeed = 12;
    
    public ScrollWidget(UIComponent child) {
        this.child = child;
    }
    
    public ScrollWidget child(UIComponent child) {
        this.child = child;
        scrollOffset = 0;
        targetScrollOffset = 0;
        return this;
    }
    
    public ScrollWidget scrollSpeed(int linesPerNotch) {
        this.scrollSpeed = linesPerNotch;
        return this;
    }
    
    public void scrollTo(int offset) {
        targetScrollOffset = clampOffset(offset);
    }
    
    public int contentWidth() {
        return Math.max(1, width - SCROLL_BAR_WIDTH - 2);
    }
    
    private int clampOffset(int requested) {
        return MathHelper.clamp(requested, 0, maxOffset());
    }
    
    private int maxOffset() {
        return Math.max(0, contentHeight - height);
    }
    
    private int visibleOffset() {
        return Math.round(scrollOffset);
    }
    
    // ---------------------------------------------------------------- layout
    
    @Override
    public void layout(int parentWidthHint, int parentHeightHint) {
        if (width == 0) width = parentWidthHint;
        if (height == 0) height = parentHeightHint;
        if (child == null) return;
        
        // child gets the full inner width minus scroll bar gutter
        int innerW = contentWidth();
        int childW = innerW;
        int childH = child.getPreferredHeight(childW);
        
        // child is laid out at our origin; scroll offset is applied at render time
        // via matrix translate so deep grandchildren do not need re-layout per scroll.
        child.setLayoutSize(childW, childH);
        child.setPosition(x, y);
        child.layout(childW, childH);
        
        contentHeight = child instanceof FlowWidget flow ? Math.max(childH, flow.laidOutHeight()) : Math.max(childH, child.getHeight());
        targetScrollOffset = clampOffset(targetScrollOffset);
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxOffset());
    }
    
    @Override
    public int getPreferredWidth(int widthHint) {
        if (preferredWidth > 0) return preferredWidth;
        if (child == null) return widthHint > 0 ? widthHint : 0;
        int childHint = widthHint > 0 ? Math.max(1, widthHint - SCROLL_BAR_WIDTH - 2) : -1;
        return child.getPreferredWidth(childHint) + SCROLL_BAR_WIDTH + 2;
    }
    
    @Override
    public int getPreferredHeight(int widthHint) {
        return preferredHeight > 0 ? preferredHeight : height;
    }
    
    // ---------------------------------------------------------------- render
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible || child == null) return;
        
        if (!surface.isNone()) {
            surface.render(context, x, y, width, height);
        }
        
        boolean hovered = isInBounds(mouseX, mouseY);
        if (hovered) scrollBarVisibleFrames = SCROLL_BAR_VISIBLE_TICKS;
        updateScrollOffset();
        int visibleOffset = visibleOffset();
        
        // Clip viewport, then translate to apply scroll offset
        context.enableScissor(x, y, x + width, y + height);
        context.getMatrices().push();
        context.getMatrices().translate(0, -visibleOffset, 0);
        child.render(context, mouseX, mouseY + visibleOffset, delta);
        context.getMatrices().pop();
        context.disableScissor();
        
        renderEdgeFades(context, visibleOffset);
        renderScrollBar(context, visibleOffset);
        
        if (scrollBarVisibleFrames > 0) scrollBarVisibleFrames--;
    }
    
    private void updateScrollOffset() {
        targetScrollOffset = clampOffset(targetScrollOffset);
        float distance = targetScrollOffset - scrollOffset;
        if (Math.abs(distance) < 0.5f) {
            scrollOffset = targetScrollOffset;
        } else {
            scrollOffset += distance * SCROLL_LERP;
        }
    }
    
    private void renderEdgeFades(DrawContext context, int visibleOffset) {
        // Top fade — only if scrolled away from top
        if (visibleOffset > 0) {
            int top = Math.min(visibleOffset, FADE_HEIGHT);
            int alpha = MathHelper.clamp(top * 32, 0, 0xC0);
            context.fillGradient(x, y, x + width - SCROLL_BAR_WIDTH - 2, y + top, (alpha << 24) | 0x000000, 0x00000000);
        }
        // Bottom fade — only if more content below
        int hidden = Math.max(0, contentHeight - height - visibleOffset);
        if (hidden > 0) {
            int bot = Math.min(hidden, FADE_HEIGHT);
            int alpha = MathHelper.clamp(bot * 32, 0, 0xC0);
            context.fillGradient(x, y + height - bot, x + width - SCROLL_BAR_WIDTH - 2, y + height, 0x00000000, (alpha << 24) | 0x000000);
        }
    }
    
    private void renderScrollBar(DrawContext context, int visibleOffset) {
        if (contentHeight <= height) return;
        if (scrollBarVisibleFrames <= 0) return;
        
        float alphaRatio = MathHelper.clamp(scrollBarVisibleFrames / (float) SCROLL_BAR_VISIBLE_TICKS, 0f, 1f);
        int trackAlpha = (int) (0x40 * alphaRatio);
        int thumbAlpha = (int) (0xC0 * alphaRatio);
        
        int barX = x + width - SCROLL_BAR_WIDTH;
        int barY = y;
        int barH = height;
        // track
        context.fill(barX, barY, barX + SCROLL_BAR_WIDTH, barY + barH, (trackAlpha << 24) | 0x000000);
        // thumb
        int thumbH = Math.max(15, (int) (barH * (height / (float) contentHeight)));
        int thumbY = barY + (int) ((barH - thumbH) * (visibleOffset / (float) Math.max(1, contentHeight - height)));
        context.fill(barX + 1, thumbY, barX + SCROLL_BAR_WIDTH - 1, thumbY + thumbH, (thumbAlpha << 24) | 0xFFFFFF);
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        // unused — render() is fully overridden
    }
    
    @Override
    public void tick() {
        if (child != null) child.tick();
    }
    
    // ---------------------------------------------------------------- mouse
    
    @Override
    public boolean handleMouseScroll(double mouseX, double mouseY, double scrollDelta) {
        if (!isInBounds(mouseX, mouseY)) return false;
        if (contentHeight <= height) return false;
        int delta = -(int) scrollDelta * scrollSpeed;
        targetScrollOffset = clampOffset(targetScrollOffset + delta);
        scrollBarVisibleFrames = SCROLL_BAR_VISIBLE_TICKS;
        return true;
    }
    
    @Override
    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (!isInBounds(mouseX, mouseY) || child == null) return false;
        return child.handleClick(mouseX, mouseY + visibleOffset(), button);
    }
    
    @Override
    public boolean handleDrag(double mouseX, double mouseY, double dx, double dy, int button) {
        if (child == null) return false;
        return child.handleDrag(mouseX, mouseY + visibleOffset(), dx, dy, button);
    }
    
    @Override
    public boolean handleMouseRelease(double mouseX, double mouseY, int button) {
        if (child == null) return false;
        return child.handleMouseRelease(mouseX, mouseY + visibleOffset(), button);
    }
    
    @Override
    public List<Text> tooltip(int mouseX, int mouseY) {
        if (child != null && isInBounds(mouseX, mouseY)) {
            int virtualY = mouseY + visibleOffset();
            if (child.isInBounds(mouseX, virtualY)) {
                var t = child.tooltip(mouseX, virtualY);
                if (t != null && !t.isEmpty()) return t;
            }
        }
        return super.tooltip(mouseX, mouseY);
    }
}
