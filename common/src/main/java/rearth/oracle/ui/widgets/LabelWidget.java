package rearth.oracle.ui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Multi-line text label. Wraps to {@link #wrapWidth} (or its explicit width if
 * no wrap is set) and reports {@link #getPreferredWidth(int)} /
 * {@link #getPreferredHeight(int)} based on the wrapped layout.
 *
 * <p>Supports a render-time {@link #scale}, {@link #color}, and click-handling
 * for URL and wiki-link styles via a {@link Predicate} that can veto navigation.</p>
 */
public class LabelWidget extends UIComponent {
    
    private Component text;
    private float scale = 1.0f;
    private int color = 0xFFFFFFFF;
    private int lineSpacing = 0;
    /**
     * -1 means "use the layout-supplied hint".
     */
    private int wrapWidth = -1;
    private boolean fillWidth = false;
    
    private Predicate<String> linkHandler;
    
    // Cached wrap result
    private List<FormattedCharSequence> wrappedLines = new ArrayList<>();
    private int lastWrapWidth = -1;
    private Component lastWrappedText;
    private float lastWrapScale = -1;
    
    public LabelWidget(Component text) {
        this.text = text;
    }
    
    public LabelWidget text(Component text) {
        this.text = text;
        invalidateWrap();
        return this;
    }
    
    public Component text() {
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
    
    public LabelWidget lineSpacing(int lineSpacing) {
        this.lineSpacing = lineSpacing;
        invalidateWrap();
        return this;
    }
    
    /**
     * Set an explicit wrap width (in unscaled pixels). -1 means use the layout-supplied hint.
     */
    public LabelWidget wrapWidth(int wrapWidth) {
        this.wrapWidth = wrapWidth;
        invalidateWrap();
        return this;
    }
    
    public LabelWidget fillWidth() {
        this.fillWidth = true;
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
    
    private Font textRenderer() {
        return Minecraft.getInstance().font;
    }
    
    /**
     * Width in unscaled font pixels available for wrapping, given a layout hint.
     */
    private int effectiveWrapWidthPx(int widthHint) {
        int avail;
        if (wrapWidth > 0) avail = wrapWidth;
        else if (widthHint > 0) avail = widthHint;
        else avail = Integer.MAX_VALUE / 2;
        // wrap is done in the unscaled font space, so undo the scale
        return Math.max(1, (int) Math.floor(avail / scale));
    }
    
    private List<FormattedCharSequence> wrap(int widthHint) {
        int wrapPx = effectiveWrapWidthPx(widthHint);
        if (wrapPx == lastWrapWidth && text == lastWrappedText && scale == lastWrapScale && !wrappedLines.isEmpty()) {
            return wrappedLines;
        }
        lastWrapWidth = wrapPx;
        lastWrappedText = text;
        lastWrapScale = scale;
        wrappedLines = textRenderer().split(text, wrapPx);
        return wrappedLines;
    }
    
    @Override
    public int getPreferredWidth(int widthHint) {
        if (preferredWidth > 0) return preferredWidth;
        if (fillWidth && widthHint > 0) return widthHint;
        var lines = wrap(widthHint);
        int max = 0;
        for (var line : lines) max = Math.max(max, textRenderer().width(line));
        return Mth.ceil(max * scale);
    }
    
    @Override
    public int getPreferredHeight(int widthHint) {
        if (preferredHeight > 0) return preferredHeight;
        var lines = wrap(widthHint);
        int n = lines.size();
        int total = n * textRenderer().lineHeight + Math.max(0, n - 1) * lineSpacing;
        return Mth.ceil(total * scale);
    }
    
    @Override
    public void layout(int parentWidthHint, int parentHeightHint) {
        // Ensure wrap is valid for current layout; size remains externally driven.
        wrap(parentWidthHint);
    }
    
    @Override
    protected void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        var lines = wrap(width > 0 ? width : Integer.MAX_VALUE / 2);
        var tr = textRenderer();
        var matrices = context.pose();
        boolean scaled = scale != 1.0f;
        if (scaled) {
            matrices.pushMatrix();
            matrices.translate(x, y);
            matrices.scale(scale, scale);
        }
        int baseX = scaled ? 0 : x;
        int baseY = scaled ? 0 : y;
        int lineHeight = tr.lineHeight + lineSpacing;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            context.text(tr, line, baseX, baseY + i * lineHeight, color, false);
        }
        if (scaled) matrices.popMatrix();
    }
    
    @Override
    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (linkHandler == null || button != 0) return super.handleClick(mouseX, mouseY, button);
        var style = styleAt(mouseX, mouseY);
        if (style == null) return super.handleClick(mouseX, mouseY, button);
        var click = style.getClickEvent();
        String destination;
        if (click instanceof ClickEvent.OpenUrl openUrl) {
            destination = openUrl.uri().toString();
        } else if (click instanceof ClickEvent.Custom custom) {
            destination = custom.payload().flatMap(tag -> tag.asString()).orElse(null);
        } else {
            return super.handleClick(mouseX, mouseY, button);
        }
        if (destination != null && linkHandler.test(destination)) return true;
        return super.handleClick(mouseX, mouseY, button);
    }
    
    /**
     * Returns the {@link Style} under the given mouse position, or null.
     */
    public Style styleAt(double mouseX, double mouseY) {
        if (!isInBounds(mouseX, mouseY)) return null;
        var tr = textRenderer();
        var lines = wrap(width > 0 ? width : Integer.MAX_VALUE / 2);
        // map screen coords to unscaled local font coords
        double localX = (mouseX - x) / scale;
        double localY = (mouseY - y) / scale;
        int lineHeight = tr.lineHeight + lineSpacing;
        int lineIndex = (int) Math.floor(localY / lineHeight);
        if (lineIndex < 0 || lineIndex >= lines.size()) return null;
        float[] measuredWidth = {0};
        Style[] result = {null};
        lines.get(lineIndex).accept((position, style, codePoint) -> {
            measuredWidth[0] += tr.width(new String(Character.toChars(codePoint)));
            if (measuredWidth[0] >= localX) {
                result[0] = style;
                return false;
            }
            return true;
        });
        return result[0];
    }
    
}
