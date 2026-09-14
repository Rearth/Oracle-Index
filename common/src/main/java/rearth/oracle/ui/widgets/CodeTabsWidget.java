package rearth.oracle.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class CodeTabsWidget extends FlowWidget {
    private static final int CODE_COLOR = 0xFFB0B4BC;

    private final List<Tab> tabs;
    private final List<LabelWidget> tabLabels = new ArrayList<>();
    private final List<ClickableWidget> tabButtons = new ArrayList<>();
    private final LabelWidget codeLabel;

    private int active;

    public CodeTabsWidget(List<Tab> tabs) {
        super(Direction.VERTICAL);
        this.tabs = List.copyOf(tabs);
        this.codeLabel = new LabelWidget(codeText(0)).color(CODE_COLOR).lineSpacing(1);

        FlowWidget header = FlowWidget.horizontal().gap(-1);
        for (int i = 0; i < this.tabs.size(); i++) {
            int index = i;
            LabelWidget label = new LabelWidget(tabTitle(i));
            tabLabels.add(label);
            
            ClickableWidget button = new ClickableWidget(label, b -> select(index))
                .centerChild()
                .selected(i == 0)
                .surfaces(WikiSurface.BEDROCK_PANEL_DARK, WikiSurface.BEDROCK_PANEL_HOVER,
                    WikiSurface.BEDROCK_PANEL_PRESSED, WikiSurface.BEDROCK_PANEL, WikiSurface.BEDROCK_PANEL_DARK);
            button.setPadding(Insets.of(5, 10));
            tabButtons.add(button);
            header.child(button);
        }

        FlowWidget body = FlowWidget.vertical();
        body.setSurface(WikiSurface.BEDROCK_PANEL_DARK);
        body.setPadding(Insets.of(6));
        body.child(codeLabel);

        super.child(header);
        super.child(body);
    }

    private Text tabTitle(int index) {
        return Text.literal(tabs.get(index).title()).formatted(Formatting.DARK_GRAY);
    }

    private Text codeText(int index) {
        return Text.literal(tabs.get(index).code().stripTrailing());
    }

    private void select(int index) {
        if (index == active || index < 0 || index >= tabs.size()) return;

        active = index;
        for (int i = 0; i < tabs.size(); i++) {
            tabLabels.get(i).text(tabTitle(i));
            tabButtons.get(i).selected(i == index);
        }
        codeLabel.text(codeText(index));

        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        requestLayout();
    }

    public record Tab(String title, String code) {
    }
}
