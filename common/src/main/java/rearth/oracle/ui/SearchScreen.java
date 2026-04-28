package rearth.oracle.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.objecthunter.exp4j.ExpressionBuilder;
import rearth.oracle.Oracle;
import rearth.oracle.OracleClient;
import rearth.oracle.SemanticSearch;
import rearth.oracle.progress.OracleProgressAPI;
import rearth.oracle.ui.widgets.*;
import rearth.oracle.util.MarkdownParser;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static rearth.oracle.OracleClient.ROOT_DIR;

public class SearchScreen extends WikiBaseScreen {
    
    private static final String MATH_EXPR_REGEX = "^[\\d\\s+\\-*/%().]+$";
    private static final int PANEL_WIDTH = 350;
    private static final int SEARCH_HEADER_HEIGHT = 44;
    private static final int RESULT_PANEL_PADDING = 6;
    
    private final Screen parent;
    private FlowWidget mainContainer;
    private FlowWidget resultsPanel;
    private ScrollWidget resultsScroll;
    private TextureWidget oracleIcon;
    private FlowWidget searchPanelBg;
    private TextFieldWidget searchField;
    private int waitFrames = 0;
    
    public SearchScreen(Screen parent) {
        super(Text.translatable("oracle_index.title.search"));
        this.parent = parent;
        backgroundFillColor = 0x99191923;
    }
    
    @Override
    protected void buildRoots() {
        mainContainer = FlowWidget.vertical().gap(4);
        mainContainer.size(PANEL_WIDTH, 0); // height resolved at layout
        
        // header row: oracle icon + search bar background panel
        oracleIcon = new TextureWidget(Identifier.of(Oracle.MOD_ID, "textures/oracle-index-icon.png"), 256, 256);
        oracleIcon.size(42, 42);
        searchPanelBg = FlowWidget.horizontal();
        searchPanelBg.setSurface(WikiSurface.BEDROCK_PANEL);
        searchPanelBg.setPadding(Insets.of(5, 7));
        searchPanelBg.size(PANEL_WIDTH - 42 - 6, 26);
        var headerRow = FlowWidget.horizontal().gap(6);
        headerRow.verticalAlignment(FlowWidget.VerticalAlignment.CENTER);
        headerRow.size(PANEL_WIDTH, SEARCH_HEADER_HEIGHT);
        headerRow.child(oracleIcon).child(searchPanelBg);
        mainContainer.child(headerRow);
        
        // results
        resultsPanel = FlowWidget.vertical().gap(2);
        resultsPanel.setPadding(Insets.of(RESULT_PANEL_PADDING, RESULT_PANEL_PADDING, RESULT_PANEL_PADDING, RESULT_PANEL_PADDING));
        resultsScroll = new ScrollWidget(resultsPanel);
        mainContainer.child(resultsScroll);
        
        addRoot(mainContainer);
        
        // vanilla text field — sized/positioned in layoutWidgets()
        searchField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, 0, 0, 1, 14, Text.empty());
        searchField.setMaxLength(120);
        searchField.setDrawsBackground(false);
        searchField.setEditable(false);
        searchField.setChangedListener(this::onSearchTyped);
        searchField.setSuggestion(Text.translatable("oracle_index.searchbar.placeholder").getString());
        searchField.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("oracle_index.searchbar.tooltip")));
        addDrawableChild(searchField);
        setInitialFocus(searchField);
    }
    
    @Override
    protected void layoutWidgets() {
        int contentH = (int) (this.height * 0.95f);
        mainContainer.setLayoutSize(PANEL_WIDTH, contentH);
        mainContainer.setPosition((this.width - PANEL_WIDTH) / 2, (this.height - contentH) / 2);
        
        // place results scroll under header
        resultsScroll.setLayoutSize(PANEL_WIDTH, contentH - SEARCH_HEADER_HEIGHT - 8);
        
        mainContainer.layout(PANEL_WIDTH, contentH);
        
        // Position vanilla text field on top of the searchPanelBg interior
        int fieldX = searchPanelBg.getX() + searchPanelBg.getPadding().left();
        int fieldY = searchPanelBg.getY() + (searchPanelBg.getHeight() - 14) / 2;
        int fieldW = searchPanelBg.getWidth() - searchPanelBg.getPadding().horizontal();
        searchField.setX(fieldX);
        searchField.setY(fieldY);
        searchField.setWidth(fieldW);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean searchReady;
        try {
            searchReady = OracleClient.getOrCreateSearch().isReady();
        } catch (Throwable error) {
            error.printStackTrace();
            var p = MinecraftClient.getInstance().player;
            if (p != null) {
                p.sendMessage(Text.literal("Sorry, Oracle Index Search is not available on your platform."));
                p.sendMessage(Text.literal("If you want this search feature, you can use the xplat jar available on the mods github."));
                p.sendMessage(Text.literal("https://github.com/Rearth/Oracle-Index"));
                p.sendMessage(Text.literal("This is not the default file due to jar size limitations."));
            }
            this.close();
            return;
        }
        
        if (searchReady) {
            if (!searchField.isActive()) searchField.setEditable(true);
            if (searchField.getText().startsWith("Indexing")) searchField.setText("");
        } else {
            waitFrames++;
            int dots = (waitFrames / 2) % 3 + 1;
            searchField.setEditable(false);
            searchField.setText("Indexing" + ".".repeat(dots));
            searchField.setSuggestion("");
        }
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void onSearchTyped(String query) {
        if (query.startsWith("Indexing")) return;
        var placeholder = Text.translatable("oracle_index.searchbar.placeholder").getString();
        searchField.setSuggestion(query.isEmpty() ? placeholder : "");
        if (query.length() <= 2) return;
        
        List<SemanticSearch.SearchResult> results;
        var expr = tryProcessExpression(query);
        if (expr.isEmpty() && query.matches(MATH_EXPR_REGEX)) return;
        
        resultsPanel.clearChildren();
        results = expr.<List<SemanticSearch.SearchResult>>map(List::of)
                      .orElseGet(() -> OracleClient.getOrCreateSearch().search(query));
        
        for (var result : results) {
            int rowWidth = resultsContentWidth();
            var contentId = Identifier.of(Oracle.MOD_ID, String.format("%s/%s/%s",
                ROOT_DIR, result.id().getNamespace(), result.id().getPath()));
            
            // gate by unlocks
            if (OracleClient.UNLOCK_CRITERIONS.containsKey(contentId.getPath())) {
                var unlock = OracleClient.UNLOCK_CRITERIONS.get(contentId.getPath());
                if (!OracleProgressAPI.IsUnlocked(result.id().getNamespace(), result.id().getPath(), unlock.getLeft(), unlock.getRight())) continue;
            }
            
            // title bar
            var titleRow = FlowWidget.horizontal().gap(4);
            titleRow.setSurface(WikiSurface.BEDROCK_PANEL_DARK);
            titleRow.setPadding(Insets.of(6, 7, 6, 8));
            titleRow.size(rowWidth, 0);
            if (result.iconName() != null && Registries.ITEM.containsId(Identifier.of(result.iconName()))) {
                var iconStack = new ItemStack(Registries.ITEM.get(Identifier.of(result.iconName())));
                var icon = new ItemWidget(iconStack);
                icon.size(14, 14);
                titleRow.child(icon);
            }
            titleRow.child(new LabelWidget(Text.literal(result.title()).formatted(Formatting.BOLD)));
            
            // body preview
            var body = FlowWidget.vertical().gap(1);
            body.setSurface(WikiSurface.BEDROCK_PANEL);
            body.setPadding(Insets.of(7, 8, 4, 4));
            body.size(rowWidth, 0);
            
            for (var text : result.texts()) {
                var widgets = MarkdownParser.parseMarkdownToWidgets(text, result.id().getNamespace(),
                    result.id(), s -> false, rowWidth - 16);
                boolean hadContent = false;
                for (var w : widgets) {
                    if (w instanceof LabelWidget label && label.scale() == 1f) {
                        label.lineSpacing(0);
                        body.child(label);
                        hadContent = true;
                    }
                }
                if (hadContent) break;
            }
            
            var openTarget = contentId;
            var openWiki = result.id().getNamespace();
            var resultRow = new SearchResultRow(titleRow, body, openWiki, openTarget);
            resultRow.fillWidth();
            resultsPanel.child(resultRow);
        }
        requestLayout();
    }
    
    private int resultsContentWidth() {
        int scrollWidth = resultsScroll != null && resultsScroll.getWidth() > 0 ? resultsScroll.getWidth() : PANEL_WIDTH;
        return Math.max(80, scrollWidth - RESULT_PANEL_PADDING * 2 - 8);
    }
    
    /** Two stacked panels (title + body) that open the linked entry on click. */
    private class SearchResultRow extends ClickableWidget {
        private final FlowWidget titleRow;
        private final FlowWidget body;
        
        SearchResultRow(FlowWidget titleRow, FlowWidget body, String wikiId, Identifier target) {
            super(buildResultContent(titleRow, body), b -> OracleClient.openScreen(wikiId, target, SearchScreen.this));
            this.titleRow = titleRow;
            this.body = body;
            surfaces(WikiSurface.NONE, WikiSurface.BEDROCK_PANEL_HOVER, WikiSurface.BEDROCK_PANEL_PRESSED,
                WikiSurface.NONE, WikiSurface.NONE);
        }
        
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hovered = isInBounds(mouseX, mouseY);
            titleRow.setSurface(hovered ? WikiSurface.BEDROCK_PANEL_PRESSED : WikiSurface.BEDROCK_PANEL_DARK);
            body.setSurface(hovered ? WikiSurface.BEDROCK_PANEL_HOVER : WikiSurface.BEDROCK_PANEL);
            super.render(context, mouseX, mouseY, delta);
        }
    }
    
    private static FlowWidget buildResultContent(FlowWidget titleRow, FlowWidget body) {
        var content = FlowWidget.vertical().gap(0);
        content.child(titleRow);
        if (!body.children().isEmpty()) content.child(body);
        return content;
    }
    
    private Optional<SemanticSearch.SearchResult> tryProcessExpression(String input) {
        try {
            var expression = new ExpressionBuilder(input).build();
            var valid = expression.validate();
            if (valid.isValid()) {
                var n = expression.evaluate();
                var calc = String.format("%s = **%s**", input.replace("*", "x"),
                    new DecimalFormat("#.####").format(n));
                return Optional.of(new SemanticSearch.SearchResult(List.of(calc), 1, "Calculation: ",
                    Identifier.of(Oracle.MOD_ID, "expression"), "minecraft:comparator"));
            }
        } catch (RuntimeException ignored) {}
        return Optional.empty();
    }
    
    @Override
    public void close() {
        Objects.requireNonNull(client).setScreen(parent);
    }
}
