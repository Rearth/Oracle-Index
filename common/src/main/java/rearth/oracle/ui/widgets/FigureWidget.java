package rearth.oracle.ui.widgets;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class FigureWidget extends FlowWidget {
    private final HorizontalAlignment alignment;

    public FigureWidget(UIComponent image, Component caption, HorizontalAlignment alignment) {
        super(Direction.VERTICAL);

        this.alignment = alignment;

        gap(3);
        horizontalAlignment(HorizontalAlignment.CENTER);
        super.child(image);
        super.child(new LabelWidget(caption.copy().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY))
            .textAlignment(HorizontalAlignment.CENTER)
            .lineSpacing(1));
    }

    @Override
    public HorizontalAlignment getOverrideAlignment() {
        return alignment;
    }
}
