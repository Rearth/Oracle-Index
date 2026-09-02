package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;

public class BlockQuoteWidget extends FlowWidget {
    private static final int RULE_COLOR = 0x80777777;
    private static final int RULE_WIDTH = 2;

    public BlockQuoteWidget() {
        super(Direction.VERTICAL);

        gap(2);
        setPadding(Insets.of(2, 0, 2, 10));
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(x + 2, y, x + 2 + RULE_WIDTH, y + height, RULE_COLOR);
        super.renderContent(context, mouseX, mouseY, delta);
    }
}
