package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import rearth.oracle.util.AudioPlayer;

public class AudioWidget extends FlowWidget {
    private static final String CHAR_PLAY = "▶";
    private static final String CHAR_STOP = "■";
    private static final int BUTTON_SIZE = 16;

    private final Identifier location;
    private final LabelWidget glyph;
    private boolean wasPlaying;

    public AudioWidget(Identifier location, String displayName) {
        super(Direction.HORIZONTAL);
        this.location = location;
        this.glyph = new LabelWidget(glyphText(false));

        setSurface(WikiSurface.BEDROCK_PANEL_DARK);
        setPadding(Insets.of(5, 7));
        gap(5);
        verticalAlignment(VerticalAlignment.CENTER);

        ClickableWidget button = new ClickableWidget(glyph, b -> {
            AudioPlayer.toggle(location);
            refreshGlyph();
        })
            .fixedSize(BUTTON_SIZE, BUTTON_SIZE)
            .centerChild()
            .surfaces(WikiSurface.BEDROCK_PANEL, WikiSurface.BEDROCK_PANEL_HOVER,
                WikiSurface.BEDROCK_PANEL_PRESSED, WikiSurface.BEDROCK_PANEL, WikiSurface.BEDROCK_PANEL_DISABLED);
        button.setPadding(new Insets(1, 0, 0, 2));

        super.child(button);
        super.child(new LabelWidget(Text.literal(displayName).formatted(Formatting.GRAY)));
    }

    private static Text glyphText(boolean playing) {
        return Text.literal(playing ? CHAR_STOP : CHAR_PLAY).formatted(Formatting.DARK_GRAY);
    }

    private void refreshGlyph() {
        boolean playing = AudioPlayer.isPlaying(location);
        if (playing == wasPlaying) return;

        wasPlaying = playing;
        glyph.text(glyphText(playing));
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        refreshGlyph();
        super.renderContent(context, mouseX, mouseY, delta);
    }
}
