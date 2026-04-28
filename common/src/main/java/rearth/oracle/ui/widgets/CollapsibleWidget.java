package rearth.oracle.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.Consumer;

/**
 * A vertical container with a clickable header that toggles its body
 * children's visibility. Used for the sidebar directory tree.
 *
 * <p>Header shows a ▸ / ▾ glyph plus the {@link #title}, and changes
 * background on hover.</p>
 */
public class CollapsibleWidget extends FlowWidget {
    
    private static final String GLYPH_CLOSED = "▸ ";
    private static final String GLYPH_OPEN = "▾ ";
    
    private final FlowWidget header;
    private final FlowWidget body;
    private final LabelWidget headerLabel;
    private Text title;
    private boolean expanded;
    private Consumer<CollapsibleWidget> onToggle;
    
    public CollapsibleWidget(Text title, boolean expanded) {
        super(Direction.VERTICAL);
        this.title = title;
        this.expanded = expanded;
        
        this.header = FlowWidget.horizontal();
        this.headerLabel = new LabelWidget(prefixedTitle());
        header.child(headerLabel);
        header.gap(0);
        
        this.body = FlowWidget.vertical().gap(2);
        body.setPadding(Insets.of(2, 0, 8, 0));
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
        headerLabel.text(prefixedTitle());
        requestLayout();
    }
    
    public void toggle() {
        setExpanded(!expanded);
        if (onToggle != null) onToggle.accept(this);
    }
    
    private Text prefixedTitle() {
        return Text.literal(expanded ? GLYPH_OPEN : GLYPH_CLOSED).formatted(Formatting.GRAY).append(title);
    }
    
    public Text title() { return title; }
    
    public CollapsibleWidget title(Text title) {
        this.title = title;
        headerLabel.text(prefixedTitle());
        return this;
    }
    
    public FlowWidget body() { return body; }
    
    @Override
    public boolean handleClick(double mouseX, double mouseY, int button) {
        // header gets first dibs
        if (button == 0 && header.isInBounds(mouseX, mouseY)) {
            toggle();
            MinecraftClient.getInstance().getSoundManager()
                .play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.handleClick(mouseX, mouseY, button);
    }
}
