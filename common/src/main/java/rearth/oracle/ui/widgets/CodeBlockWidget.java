package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

public class CodeBlockWidget extends FlowWidget {
    private static final int CODE_COLOR = 0xFFB0B4BC;
    private static final int DIVIDER_COLOR = 0x40FFFFFF;

    @Nullable
    private final LabelWidget header;

    public CodeBlockWidget(@Nullable String fileName, @Nullable String language, String code) {
        super(Direction.VERTICAL);
        setSurface(WikiSurface.BEDROCK_PANEL_DARK);
        setPadding(Insets.of(6));
        gap(4);

        MutableText headerText = headerText(fileName, language);
        this.header = headerText == null ? null : new LabelWidget(headerText);
        if (header != null) super.child(header);

        super.child(new LabelWidget(Text.literal(code.stripTrailing())).color(CODE_COLOR).lineSpacing(1));
    }

    @Nullable
    private static MutableText headerText(@Nullable String fileName, @Nullable String language) {
        boolean hasFile = fileName != null && !fileName.isBlank();
        boolean hasLanguage = language != null && !language.isBlank();
        if (!hasFile && !hasLanguage) return null;

        if (!hasFile) {
            return Text.literal(language).formatted(Formatting.GRAY);
        }

        return Text.literal(fileName).formatted(Formatting.GOLD);
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderContent(context, mouseX, mouseY, delta);
        if (header == null) return;

        int lineY = header.getY() + header.getHeight() + 1;
        context.fill(x + padding.left(), lineY, x + width - padding.right(), lineY + 1, DIVIDER_COLOR);
    }
}
