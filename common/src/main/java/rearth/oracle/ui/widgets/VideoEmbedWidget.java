package rearth.oracle.ui.widgets;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

public class VideoEmbedWidget extends FlowWidget {
    private static final String YOUTUBE_URL = "https://www.youtube.com/watch?v=";
    private static final String PLAY_ICON = "▶";

    public VideoEmbedWidget(String videoId, Predicate<String> linkHandler) {
        super(Direction.VERTICAL);

        String url = YOUTUBE_URL + videoId;
        FlowWidget caption = FlowWidget.horizontal().gap(6);
        caption.verticalAlignment(VerticalAlignment.CENTER);
        caption.child(new LabelWidget(Component.literal(PLAY_ICON).withStyle(ChatFormatting.RED)).scale(1.5F));
        caption.child(new LabelWidget(Component.translatable("oracle_index.video.watch").withStyle(ChatFormatting.DARK_GRAY)));

        ClickableWidget button = new ClickableWidget(caption, b -> linkHandler.test(url))
            .centerChild()
            .surfaces(WikiSurface.BEDROCK_PANEL, WikiSurface.BEDROCK_PANEL_HOVER,
                WikiSurface.BEDROCK_PANEL_PRESSED, WikiSurface.BEDROCK_PANEL, WikiSurface.BEDROCK_PANEL_DISABLED);
        button.setPadding(Insets.of(10, 16));

        horizontalAlignment(HorizontalAlignment.CENTER);
        super.child(button);
    }

    @Override
    public HorizontalAlignment getOverrideAlignment() {
        return HorizontalAlignment.CENTER;
    }
}
