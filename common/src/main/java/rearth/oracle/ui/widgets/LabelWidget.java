package rearth.oracle.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Multi-line text label. Wraps to {@link #wrapWidth} (or its explicit width if
 * no wrap is set) and reports {@link #getPreferredWidth(int)} /
 * {@link #getPreferredHeight(int)} based on the wrapped layout.
 *
 * <p>Supports a render-time {@link #scale}, {@link #color}, optional
 * {@link #shadow}, and click-handling for {@link ClickEvent#OPEN_URL} styles
 * via a {@link Predicate} that can veto navigation.</p>
 */
public class LabelWidget extends UIComponent {
    
    private Text text;
    private float scale = 1.0f;
    private int color = 0xFFFFFFFF;
    private boolean shadow = false;
    private int lineSpacing = 0;
    /** -1 means "use my explicit width". */
    private int wrapWidth = -1;
    private HorizontalAlignment horizontalAlignment = HorizontalAlignment.LEFT;
    
    private Predicate<String> linkHandler;
    
    // Cached wrap result
    private List<OrderedText> wrappedLines = new ArrayList<>();
    private int lastWrapWidth = -1;
    private Text lastWrappedText;
    private float lastWrapScale = -1;
    
    public LabelWidget(Text text) {
        this.text = text;
    }
    
    public LabelWidget text(Text text) {
        this.text = text;
        invalidateWrap();
        return this;
    }
    
    public Text text() {
        return text;
    }
    
    public LabelWidget scale(float scale) {
        this.scale = scale;
        invalidateWrap();
        return this;
    }
    
    public float scale() {
        return scale;
    }
    
    public LabelWidget color(int argb) {
        this.color = argb;
        return this;
    }
    
    public LabelWidget shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }
    
    public LabelWidget lineSpacing(int lineSpacing) {
        this.lineSpacing = lineSpacing;
        invalidateWrap();
        return this;
    }
    
    /** Set an explicit wrap width (in unscaled pixels). -1 means use the layout-supplied hint. */
    public LabelWidget wrapWidth(int wrapWidth) {
        this.wrapWidth = wrapWidth;
        invalidateWrap();
        return this;
    }
    
    public LabelWidget horizontalAlignment(HorizontalAlignment alignment) {
        this.horizontalAlignment = alignment;
        return this;
    }
    
    public LabelWidget linkHandler(Predicate<String> handler) {
        this.linkHandler = handler;
        return this;
    }
    
    private void invalidateWrap() {
        lastWrapWidth = -1;
        lastWrappedText = null;
        lastWrapScale = -1;
    }
    
    private TextRenderer textRenderer() {
        return MinecraftClient.getInstance().textRenderer;
    }
    
    /** Width in unscaled font pixels available for wrapping, given a layout hint. */
    private int effectiveWrapWidthPx(int widthHint) {
        int avail;
        if (wrapWidth > 0) avail = wrapWidth;
        else if (width > 0) avail = width;
        else if (widthHint > 0) avail = widthHint;
        else avail = Integer.MAX_VALUE / 2;
        // wrap is done in the unscaled font space, so undo the scale
        return Math.max(1, (int) Math.floor(avail / scale));
    }
    
    private List<OrderedText> wrap(int widthHint) {
        int wrapPx = effectiveWrapWidthPx(widthHint);
        if (wrapPx == lastWrapWidth && text == lastWrappedText && scale == lastWrapScale && !wrappedLines.isEmpty()) {
            return wrappedLines;
        }
        lastWrapWidth = wrapPx;
        lastWrappedText = text;
        lastWrapScale = scale;
        wrappedLines = textRenderer().wrapLines(text, wrapPx);
        return wrappedLines;
    }
    
    @Override
    public int getPreferredWidth(int widthHint) {
        if (width > 0) return width;
        var lines = wrap(widthHint);
        int max = 0;
        for (var line : lines) max = Math.max(max, textRenderer().getWidth(line));
        return MathHelper.ceil(max * scale);
    }
    
    @Override
    public int getPreferredHeight(int widthHint) {
        if (height > 0) return height;
        var lines = wrap(widthHint);
        int n = lines.size();
        int total = n * textRenderer().fontHeight + Math.max(0, n - 1) * lineSpacing;
        return MathHelper.ceil(total * scale);
    }
    
    @Override
    public void layout(int parentWidthHint, int parentHeightHint) {
        // Ensure wrap is valid for current layout; size remains externally driven.
        wrap(parentWidthHint);
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        var lines = wrap(width > 0 ? width : Integer.MAX_VALUE / 2);
        var tr = textRenderer();
        var matrices = context.getMatrices();
        boolean scaled = scale != 1.0f;
        if (scaled) {
            matrices.push();
            matrices.translate(x, y, 0);
            matrices.scale(scale, scale, 1.0f);
        }
        int baseX = scaled ? 0 : x;
        int baseY = scaled ? 0 : y;
        int lineHeight = tr.fontHeight + lineSpacing;
        int boxWidthPx = scaled ? (int) Math.floor((width > 0 ? width : Integer.MAX_VALUE / 2) / scale) : (width > 0 ? width : Integer.MAX_VALUE / 2);
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            int lx = baseX;
            if (horizontalAlignment != HorizontalAlignment.LEFT && width > 0) {
                int lw = tr.getWidth(line);
                int slack = boxWidthPx - lw;
                if (horizontalAlignment == HorizontalAlignment.CENTER) lx = baseX + slack / 2;
                else if (horizontalAlignment == HorizontalAlignment.RIGHT) lx = baseX + slack;
            }
            context.drawText(tr, line, lx, baseY + i * lineHeight, color, shadow);
        }
        if (scaled) matrices.pop();
    }
    
    @Override
    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (linkHandler == null || button != 0) return super.handleClick(mouseX, mouseY, button);
        var style = styleAt(mouseX, mouseY);
        if (style == null) return super.handleClick(mouseX, mouseY, button);
        var click = style.getClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.OPEN_URL) return super.handleClick(mouseX, mouseY, button);
        if (linkHandler.test(click.getValue())) return true;
        return super.handleClick(mouseX, mouseY, button);
    }
    
    /** Returns the {@link Style} under the given mouse position, or null. */
    public Style styleAt(double mouseX, double mouseY) {
        if (!isInBounds(mouseX, mouseY)) return null;
        var tr = textRenderer();
        var lines = wrap(width > 0 ? width : Integer.MAX_VALUE / 2);
        // map screen coords to unscaled local font coords
        double localX = (mouseX - x) / scale;
        double localY = (mouseY - y) / scale;
        int lineHeight = tr.fontHeight + lineSpacing;
        int lineIndex = (int) Math.floor(localY / lineHeight);
        if (lineIndex < 0 || lineIndex >= lines.size()) return null;
        return tr.getTextHandler().getStyleAt(lines.get(lineIndex), MathHelper.floor(localX));
    }
    
    public enum HorizontalAlignment { LEFT, CENTER, RIGHT }
    
    /** Convenience for plain {@link MutableText}. */
    public static LabelWidget of(MutableText text) {
        return new LabelWidget(text);
    }
}
