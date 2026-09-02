package rearth.oracle.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import rearth.oracle.util.CalloutVariant;

/**
 * Stylised callout block used by {@code <Callout>} markdown tags.
 * Renders a panel for the body content with a small overlapping title chip
 * sitting on the top-left corner. The variant string ("note", "warning",
 * "tip" …) is used for the title — capitalised.
 */
public class CalloutWidget extends FlowWidget {
    private static final int BODY_TEXT_COLOR = 0xFF555555;
    private static final int LABEL_OVERLAP = 6;
    private static final int LABEL_PAD_X = 12;
    private static final int LABEL_PAD_Y = 9;

    private static final String CH_CLOSED = " >";
    private static final String CH_OPEN = " v";

    private final CalloutVariant variant;
    private final Text title;
    private final boolean collapsible;
    private final FlowWidget body;

    private boolean expanded;
    private int labelX, labelY, labelWidth, labelHeight;

    public CalloutWidget(CalloutVariant variant, @Nullable Text title, boolean collapsible, boolean collapsed) {
        super(Direction.VERTICAL);
        this.variant = variant;
        this.title = title != null ? title : variant.getTitle();
        this.collapsible = collapsible;
        this.expanded = !collapsed;
        this.body = FlowWidget.vertical();
        body.setSurface(WikiSurface.BEDROCK_PANEL);
        body.setPadding(Insets.of(14, 8, 10, 10)); // extra top so the chip doesn't overlap the text
        body.setVisible(expanded);
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
        } else if (child instanceof TableWidget table) {
            table.color(BODY_TEXT_COLOR);
        } else if (child instanceof FlowWidget flow && flow.getSurface().isNone()) {
            for (var nested : flow.children()) {
                tintBodyText(nested);
            }
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded == expanded) return;
        this.expanded = expanded;
        body.setVisible(expanded);
        requestLayout();
    }

    private Text getLabelTitle() {
        var text = title.copy().formatted(Formatting.WHITE);
        if (collapsible) text.append(Text.literal(expanded ? CH_OPEN : CH_CLOSED));
        return text;
    }

    private int getLabelHeight() {
        return MinecraftClient.getInstance().textRenderer.fontHeight + LABEL_PAD_Y;
    }

    @Override
    public int getPreferredWidth(int widthHint) {
        if (widthHint > 0) return widthHint;
        return super.getPreferredWidth(widthHint);
    }

    @Override
    public int getPreferredHeight(int widthHint) {
        if (!expanded) return getLabelHeight();
        if (widthHint > 0) return body.getPreferredHeight(calloutWidth(widthHint));
        return super.getPreferredHeight(widthHint);
    }

    @Override
    public void layout(int parentWidthHint, int parentHeightHint) {
        width = parentWidthHint > 0 ? parentWidthHint : getPreferredWidth(-1);
        int bodyWidth = calloutWidth(width);
        int bodyX = x + (width - bodyWidth) / 2;

        if (expanded) {
            int bodyHeight = body.getPreferredHeight(bodyWidth);
            height = bodyHeight;
            body.setPosition(bodyX, y);
            body.setLayoutSize(bodyWidth, bodyHeight);
            body.layout(bodyWidth, bodyHeight);
        } else {
            height = getLabelHeight();
            body.setPosition(bodyX, y + LABEL_OVERLAP);
            body.setLayoutSize(bodyWidth, 0);
        }

        var tr = MinecraftClient.getInstance().textRenderer;
        labelWidth = tr.getWidth(getLabelTitle()) + LABEL_PAD_X;
        labelHeight = getLabelHeight();
        labelX = bodyX - LABEL_OVERLAP;
        labelY = body.getY() - LABEL_OVERLAP;
    }

    private int calloutWidth(int availableWidth) {
        return Math.min(Math.max(1, availableWidth), Math.max(120, (int) (availableWidth * 0.8f)));
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderContent(context, mouseX, mouseY, delta);
        // overlapping title chip rendered on top
        var tr = MinecraftClient.getInstance().textRenderer;
        this.variant.getSurface().render(context, labelX, labelY, labelWidth, labelHeight);
        context.drawText(tr, getLabelTitle(), labelX + LABEL_OVERLAP, labelY + LABEL_OVERLAP, 0xFFFFFFFF, false);
    }

    @Override
    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (collapsible && button == 0 && isOverLabel(mouseX, mouseY)) {
            setExpanded(!expanded);
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.handleClick(mouseX, mouseY, button);
    }

    private boolean isOverLabel(double mouseX, double mouseY) {
        return mouseX >= labelX && mouseX < labelX + labelWidth && mouseY >= labelY && mouseY < labelY + labelHeight;
    }
}
