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
        body.child(child);
        return this;
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderContent(context, mouseX, mouseY, delta);
        // overlapping title chip rendered on top
        var tr = MinecraftClient.getInstance().textRenderer;
        var title = Text.literal(StringUtils.capitalize(variant)).formatted(Formatting.WHITE);
        int textW = tr.getWidth(title);
        int padX = 6, padY = 3;
        int chipW = textW + padX * 2;
        int chipH = tr.fontHeight + padY * 2;
        int chipX = x; // anchor top-left of widget
        int chipY = y;
        WikiSurface.BEDROCK_PANEL_PRESSED.render(context, chipX, chipY, chipW, chipH);
        context.drawText(tr, title, chipX + padX, chipY + padY, 0xFFFFFFFF, false);
    }
}
