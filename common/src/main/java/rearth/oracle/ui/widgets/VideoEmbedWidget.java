package rearth.oracle.ui.widgets;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Predicate;

public class VideoEmbedWidget extends FlowWidget {
    private static final String YOUTUBE_URL = "https://www.youtube.com/watch?v=";
    private static final String PLAY_ICON = "▶";

    public VideoEmbedWidget(String videoId, Predicate<String> linkHandler) {
        super(Direction.VERTICAL);

        String url = YOUTUBE_URL + videoId;
        FlowWidget caption = FlowWidget.horizontal().gap(6);
        caption.verticalAlignment(VerticalAlignment.CENTER);
        caption.child(new LabelWidget(Text.literal(PLAY_ICON).formatted(Formatting.RED)).scale(1.5F));
        caption.child(new LabelWidget(Text.translatable("oracle_index.video.watch").formatted(Formatting.DARK_GRAY)));

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
