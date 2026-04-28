package rearth.oracle.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.StringUtils;

/**
 * Stylised callout block used by {@code <Callout>} markdown tags.
 * Renders a panel for the body content with a small overlapping title chip
 * sitting on the top-left corner. The variant string ("note", "warning",
 * "tip" …) is used for the title — capitalised.
 */
public class CalloutWidget extends FlowWidget {
    private static final int BODY_TEXT_COLOR = 0xFF555555;
    
    private final String variant;
    private final FlowWidget body;
    
    public CalloutWidget(String variant) {
        super(Direction.VERTICAL);
        this.variant = variant;
        this.body = FlowWidget.vertical();
        body.setSurface(WikiSurface.BEDROCK_PANEL);
        body.setPadding(Insets.of(14, 8, 10, 10)); // extra top so the chip doesn't overlap the text
        super.child(body);
    }
    
    public CalloutWidget addBodyChild(UIComponent child) {
        tintBodyText(child);
        body.child(child);
        return this;
    }
    
    private void tintBodyText(UIComponent child) {
        if (child instanceof LabelWidget label) {
            label.color(BODY_TEXT_COLOR);
        } else if (child instanceof FlowWidget flow) {
            for (var nested : flow.children()) tintBodyText(nested);
        }
    }
    
    @Override
    public int getPreferredWidth(int widthHint) {
        if (widthHint > 0) return widthHint;
        return super.getPreferredWidth(widthHint);
    }
    
    @Override
    public int getPreferredHeight(int widthHint) {
        if (widthHint > 0) return body.getPreferredHeight(calloutWidth(widthHint));
        return super.getPreferredHeight(widthHint);
    }
    
    @Override
    public void layout(int parentWidthHint, int parentHeightHint) {
        width = parentWidthHint > 0 ? parentWidthHint : getPreferredWidth(-1);
        int bodyWidth = calloutWidth(width);
        int bodyHeight = body.getPreferredHeight(bodyWidth);
        height = bodyHeight;
        body.setPosition(x + (width - bodyWidth) / 2, y);
        body.setLayoutSize(bodyWidth, bodyHeight);
        body.layout(bodyWidth, bodyHeight);
    }
    
    private int calloutWidth(int availableWidth) {
        return Math.min(Math.max(1, availableWidth), Math.max(120, (int) (availableWidth * 0.8f)));
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderContent(context, mouseX, mouseY, delta);
        // overlapping title chip rendered on top
        var tr = MinecraftClient.getInstance().textRenderer;
        var title = Text.literal(StringUtils.capitalize(variant)).formatted(Formatting.WHITE);
        int textW = tr.getWidth(title);
        int chipW = textW + 12;
        int chipH = tr.fontHeight + 9;
        int chipX = body.getX();
        int chipY = body.getY();
        WikiSurface.BEDROCK_PANEL_PRESSED.render(context, chipX - 6, chipY - 6, chipW, chipH);
        context.drawText(tr, title, chipX, chipY, 0xFFFFFFFF, false);
    }
}
