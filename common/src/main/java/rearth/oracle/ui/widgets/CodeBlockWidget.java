package rearth.oracle.ui.widgets;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

        MutableComponent headerText = headerText(fileName, language);
        this.header = headerText == null ? null : new LabelWidget(headerText);
        if (header != null) super.child(header);

        super.child(new LabelWidget(Component.literal(code.stripTrailing())).color(CODE_COLOR).lineSpacing(1));
    }

    @Nullable
    private static MutableComponent headerText(@Nullable String fileName, @Nullable String language) {
        boolean hasFile = fileName != null && !fileName.isBlank();
        boolean hasLanguage = language != null && !language.isBlank();
        if (!hasFile && !hasLanguage) return null;

        if (!hasFile) {
            return Component.literal(language).withStyle(ChatFormatting.GRAY);
        }

        return Component.literal(fileName).withStyle(ChatFormatting.GOLD);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.renderContent(context, mouseX, mouseY, delta);
        if (header == null) return;

        int lineY = header.getY() + header.getHeight() + 1;
        context.fill(x + padding.left(), lineY, x + width - padding.right(), lineY + 1, DIVIDER_COLOR);
    }
}
