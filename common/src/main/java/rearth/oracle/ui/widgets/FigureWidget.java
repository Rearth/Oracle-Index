package rearth.oracle.ui.widgets;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class FigureWidget extends FlowWidget {
    private final HorizontalAlignment alignment;

    public FigureWidget(UIComponent image, Text caption, HorizontalAlignment alignment) {
        super(Direction.VERTICAL);

        this.alignment = alignment;

        gap(3);
        horizontalAlignment(HorizontalAlignment.CENTER);
        super.child(image);
        super.child(new LabelWidget(caption.copy().formatted(Formatting.ITALIC, Formatting.DARK_GRAY))
            .textAlignment(HorizontalAlignment.CENTER)
            .lineSpacing(1));
    }

    @Override
    public HorizontalAlignment getOverrideAlignment() {
        return alignment;
    }
}
