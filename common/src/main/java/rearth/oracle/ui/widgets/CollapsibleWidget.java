package rearth.oracle.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/**
 * A vertical container with a clickable header that toggles its body
 * children's visibility. Used for the sidebar directory tree.
 *
 * <p>Header shows a ▸ / ▾ glyph plus the title and changes background on hover.</p>
 */
public class CollapsibleWidget extends FlowWidget {
    
    private static final String GLYPH_CLOSED = " >";
    private static final String GLYPH_OPEN = " ▾";
    
    private static final int HEADER_HEIGHT = 16;
    
    private final ClickableWidget header;
    private final FlowWidget body;
    private final LabelWidget glyphLabel;
    private final LabelWidget titleLabel;
    private final Text title;
    private boolean expanded;
    private boolean headerHovered;
    private Consumer<CollapsibleWidget> onToggle;
    
    public CollapsibleWidget(Text title, boolean expanded) {
        super(Direction.VERTICAL);
        this.title = title;
        this.expanded = expanded;
        
        var headerContent = FlowWidget.horizontal().gap(2);
        headerContent.verticalAlignment(VerticalAlignment.CENTER);
        this.glyphLabel = new LabelWidget(glyph()).color(0xFFAAAAAA);
        this.titleLabel = new LabelWidget(headerTitle(false));
        headerContent.child(titleLabel).child(glyphLabel);
        this.header = new ClickableWidget(headerContent, row -> {
            toggle();
            MinecraftClient.getInstance().getSoundManager()
              .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }).fillWidth().fixedHeight(HEADER_HEIGHT)
                        .surfaces(WikiSurface.NONE, WikiSurface.NONE, WikiSurface.NONE, WikiSurface.NONE, WikiSurface.NONE);
        header.setPadding(Insets.of(1, 4));
        
        this.body = FlowWidget.vertical().gap(2);
        body.setPadding(Insets.of(1, 0, 1, 9));
        body.setVisible(expanded);
        
        super.child(header);
        super.child(body);
    }
    
    public CollapsibleWidget onToggle(Consumer<CollapsibleWidget> handler) {
        this.onToggle = handler;
        return this;
    }
    
    public CollapsibleWidget addBodyChild(UIComponent child) {
        body.child(child);
        return this;
    }
    
    public boolean expanded() {
        return expanded;
    }
    
    public void setExpanded(boolean expanded) {
        if (this.expanded == expanded) return;
        this.expanded = expanded;
        body.setVisible(expanded);
        glyphLabel.text(glyph());
        requestLayout();
    }
    
    public void toggle() {
        setExpanded(!expanded);
        if (onToggle != null) onToggle.accept(this);
    }
    
    private Text glyph() {
        return Text.literal(expanded ? GLYPH_OPEN : GLYPH_CLOSED).formatted(Formatting.GRAY);
    }
    
    private Text headerTitle(boolean hovered) {
        return hovered
                 ? title.copy().formatted(Formatting.WHITE, Formatting.GRAY)
                 : title.copy().formatted(Formatting.WHITE);
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = header.isInBounds(mouseX, mouseY);
        if (hovered != headerHovered) {
            headerHovered = hovered;
            titleLabel.text(headerTitle(hovered));
        }
        if (expanded && body.isVisible()) {
            int lineX = body.getX() + 4;
            int top = body.getY() + 2;
            int bottom = body.getY() + body.getHeight() - 2;
            if (bottom > top) context.fill(lineX, top, lineX + 1, bottom, 0x80777777);
        }
        super.renderContent(context, mouseX, mouseY, delta);
    }
    
    public FlowWidget body() {
        return body;
    }
    
}
