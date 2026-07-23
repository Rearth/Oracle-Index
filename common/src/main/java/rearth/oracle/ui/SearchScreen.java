package rearth.oracle.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
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
    private static final int SEARCH_HEADER_HEIGHT = 64;
    private static final int SEARCH_BAR_HEIGHT = 38;
    private static final int ORACLE_ICON_SIZE = 58;
    private static final int RESULT_PANEL_PADDING = 6;
    private static final int RESULT_TITLE_OVERLAP = 7;
    private static final int RESULT_BODY_INSET = 4;
    private static final int SEARCH_BAR_OVERLAP = 16;
    private static final int SEARCH_PANEL_PAD = 5;
    
    private final Screen parent;
    private FlowWidget mainContainer;
    private FlowWidget resultsPanel;
    private ScrollWidget resultsScroll;
    private TextureWidget oracleIcon;
    private EditBox searchField;
    private int searchBarX;
    private int searchBarY;
    private int searchBarW;
    private int searchBarH;
    private int waitFrames = 0;
    private boolean searchReady;
    
    public SearchScreen(Screen parent) {
        super(Component.translatable("oracle_index.title.search"));
        this.parent = parent;
        backgroundFillColor = 0x99191923;
    }
    
    @Override
    protected void buildRoots() {
        mainContainer = FlowWidget.vertical().gap(6);
        mainContainer.size(PANEL_WIDTH, 0); // height resolved at layout
        
        oracleIcon = new TextureWidget(Identifier.fromNamespaceAndPath(Oracle.MOD_ID, "textures/oracle-index-icon.png"), 256, 256);
        oracleIcon.size(ORACLE_ICON_SIZE, ORACLE_ICON_SIZE);
        
        // results
        resultsPanel = FlowWidget.vertical().gap(6);
        resultsPanel.setPadding(Insets.of(RESULT_PANEL_PADDING, RESULT_PANEL_PADDING, RESULT_PANEL_PADDING, RESULT_PANEL_PADDING));
        resultsScroll = new ScrollWidget(resultsPanel);
        mainContainer.child(resultsScroll);
        
        addRoot(mainContainer);
        
        // vanilla text field — sized/positioned in layoutWidgets()
        searchField = new EditBox(Minecraft.getInstance().font, 0, 0, 1, 14, Component.empty());
        searchField.setMaxLength(120);
        searchField.setBordered(false);
        searchField.setEditable(true);
        searchField.setResponder(this::onSearchTyped);
        searchField.setSuggestion(Component.translatable("oracle_index.searchbar.placeholder").getString());
        searchField.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("oracle_index.searchbar.tooltip")));
        addRenderableWidget(searchField);
        setInitialFocus(searchField);
    }
    
    @Override
    protected void layoutWidgets() {
        int contentH = (int) (this.height * 0.95f);
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - contentH) / 2;
        mainContainer.setLayoutSize(PANEL_WIDTH, contentH - SEARCH_HEADER_HEIGHT);
        mainContainer.setPosition(panelX, panelY + SEARCH_HEADER_HEIGHT);
        
        resultsScroll.setLayoutSize(PANEL_WIDTH, contentH - SEARCH_HEADER_HEIGHT);
        
        mainContainer.layout(PANEL_WIDTH, contentH - SEARCH_HEADER_HEIGHT);
        
        var barHeight = SEARCH_BAR_HEIGHT;
        
        searchBarX = panelX + ORACLE_ICON_SIZE - SEARCH_BAR_OVERLAP - 7;
        searchBarY = panelY + (SEARCH_HEADER_HEIGHT - barHeight) / 2;
        searchBarW = PANEL_WIDTH - ORACLE_ICON_SIZE + SEARCH_BAR_OVERLAP;
        searchBarH = 20;
        oracleIcon.setPosition(panelX, searchBarY + (barHeight - ORACLE_ICON_SIZE) / 2);
        oracleIcon.setLayoutSize(ORACLE_ICON_SIZE, ORACLE_ICON_SIZE);
        oracleIcon.layout(ORACLE_ICON_SIZE, ORACLE_ICON_SIZE);
        int fieldX = searchBarX + 26;
        int fieldY = searchBarY + (searchBarH - 14) / 2;
        int fieldW = searchBarW - 14;
        searchField.setX(fieldX);
        searchField.setY(fieldY + 3);
        searchField.setWidth(fieldW);
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        boolean wasReady = searchReady;
        try {
            searchReady = OracleClient.getOrCreateSearch().isReady();
        } catch (Throwable error) {
            error.printStackTrace();
            var p = Minecraft.getInstance().player;
            if (p != null) {
                p.sendSystemMessage(Component.literal("Sorry, Oracle Index Search is not available on your platform."));
                p.sendSystemMessage(Component.literal("If you want this search feature, you can use the xplat jar available on the mods github."));
                p.sendSystemMessage(Component.literal("https://github.com/Rearth/Oracle-Index"));
                p.sendSystemMessage(Component.literal("This is not the default file due to jar size limitations."));
            }
            this.onClose();
            return;
        }
        
        if (searchReady) {
            searchField.setEditable(true);
            var query = searchField.getValue();
            searchField.setSuggestion(query.isEmpty()
              ? Component.translatable("oracle_index.searchbar.placeholder").getString()
              : "");
            if (!wasReady && !query.isEmpty()) onSearchTyped(query);
        } else {
            waitFrames++;
            int dots = (waitFrames / 2) % 3 + 1;
            searchField.setEditable(true);
            searchField.setSuggestion(searchField.getValue().isEmpty() ? "Indexing" + ".".repeat(dots) : "");
        }
        
        super.extractRenderState(context, mouseX, mouseY, delta);
        renderSearchHeader(context, mouseX, mouseY, delta);
    }
    
    private void renderSearchHeader(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        WikiSurface.BEDROCK_PANEL.render(context,
          searchBarX - SEARCH_PANEL_PAD,
          searchBarY - SEARCH_PANEL_PAD,
          searchBarW + SEARCH_PANEL_PAD * 2,
          searchBarH + SEARCH_PANEL_PAD * 2);
        context.fill(searchBarX + 19, searchBarY, searchBarX + searchBarW, searchBarY + searchBarH, 0xFFFFFFFF);
        context.fill(searchBarX + 19 + 1, searchBarY + 1, searchBarX + searchBarW - 1, searchBarY + searchBarH - 1, 0xFF000000);
        searchField.extractRenderState(context, mouseX, mouseY, delta);
        oracleIcon.render(context, mouseX, mouseY, delta);
    }
    
    private void onSearchTyped(String query) {
        var placeholder = Component.translatable("oracle_index.searchbar.placeholder").getString();
        searchField.setSuggestion(query.isEmpty() ? placeholder : "");
        if (!searchReady) return;
        if (query.length() <= 2) return;
        
        List<SemanticSearch.SearchResult> results;
        var expr = tryProcessExpression(query);
        if (expr.isEmpty() && query.matches(MATH_EXPR_REGEX)) return;
        
        resultsPanel.clearChildren();
        results = expr.map(List::of)
                    .orElseGet(() -> OracleClient.getOrCreateSearch().search(query));
        
        for (var result : results) {
            int rowWidth = resultsContentWidth();
            var contentId = Identifier.fromNamespaceAndPath(Oracle.MOD_ID, String.format("%s/%s/%s",
              ROOT_DIR, result.id().getNamespace(), result.id().getPath()));
            
            // gate by unlocks
            if (OracleClient.UNLOCK_CRITERIONS.containsKey(contentId.getPath())) {
                var unlock = OracleClient.UNLOCK_CRITERIONS.get(contentId.getPath());
                if (!OracleProgressAPI.IsUnlocked(result.id().getNamespace(), result.id().getPath(), unlock.getFirst(), unlock.getSecond()))
                    continue;
            }
            
            // title bar
            var titleRow = FlowWidget.horizontal().gap(4);
            titleRow.setSurface(WikiSurface.BEDROCK_PANEL_DARK);
            titleRow.setPadding(Insets.of(6, 7, 4, 8));
            if (result.iconName() != null && BuiltInRegistries.ITEM.containsKey(Identifier.parse(result.iconName()))) {
                var iconStack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(result.iconName())));
                var icon = new ItemWidget(iconStack);
                icon.size(12, 12);
                icon.setTooltipMode(TooltipMode.HIDDEN);
                icon.setHideItemDecorations(true);
                titleRow.child(icon);
            }
            titleRow.child(new LabelWidget(Component.literal(result.title()).withStyle(ChatFormatting.BOLD)));
            
            // body preview
            var body = FlowWidget.vertical().gap(1);
            body.setSurface(WikiSurface.BEDROCK_PANEL);
            body.setPadding(Insets.of(14, 8, 4, 4));
            
            for (var text : result.texts()) {
                var widgets = MarkdownParser.parseMarkdownToWidgets(text, result.id().getNamespace(),
                  result.id(), s -> false, rowWidth - 16);
                boolean hadContent = false;
                for (var w : widgets) {
                    if (w instanceof LabelWidget label && label.scale() == 1f) {
                        label.lineSpacing(0);
                        label.color(0xFF555555);
                        body.child(label);
                        hadContent = true;
                    }
                }
                if (hadContent) break;
            }
            
            var openTarget = contentId;
            var openWiki = result.id().getNamespace();
            var resultRow = new SearchResultRow(titleRow, body, openWiki, openTarget);
            resultsPanel.child(resultRow);
        }
        requestLayout();
    }
    
    private int resultsContentWidth() {
        int scrollWidth = resultsScroll != null && resultsScroll.getWidth() > 0 ? resultsScroll.getWidth() : PANEL_WIDTH;
        return Math.max(80, scrollWidth - RESULT_PANEL_PADDING * 2 - 8);
    }
    
    /**
     * Result preview with a title chip overlapping the body preview panel.
     */
    private class SearchResultRow extends UIComponent {
        private final FlowWidget titleRow;
        private final FlowWidget body;
        private final String wikiId;
        private final Identifier target;
        private boolean pressed;
        
        SearchResultRow(FlowWidget titleRow, FlowWidget body, String wikiId, Identifier target) {
            this.titleRow = titleRow;
            this.body = body;
            this.wikiId = wikiId;
            this.target = target;
        }
        
        @Override
        public int getPreferredWidth(int widthHint) {
            return widthHint > 0 ? widthHint : PANEL_WIDTH;
        }
        
        @Override
        public int getPreferredHeight(int widthHint) {
            int bodyWidth = bodyWidth(widthHint > 0 ? widthHint : PANEL_WIDTH);
            int titleHeight = titleRow.getPreferredHeight(-1);
            int bodyHeight = body.children().isEmpty() ? 0 : body.getPreferredHeight(bodyWidth);
            return body.children().isEmpty() ? titleHeight : titleHeight + bodyHeight - RESULT_TITLE_OVERLAP;
        }
        
        @Override
        public void layout(int parentWidthHint, int parentHeightHint) {
            width = parentWidthHint > 0 ? parentWidthHint : PANEL_WIDTH;
            int bodyWidth = bodyWidth(width);
            int titleWidth = Math.min(width - RESULT_BODY_INSET, titleRow.getPreferredWidth(-1));
            int titleHeight = titleRow.getPreferredHeight(titleWidth);
            int bodyHeight = body.children().isEmpty() ? 0 : body.getPreferredHeight(bodyWidth);
            titleRow.setPosition(x, y);
            titleRow.setLayoutSize(titleWidth, titleHeight);
            titleRow.layout(titleWidth, titleHeight);
            if (!body.children().isEmpty()) {
                body.setPosition(x + RESULT_BODY_INSET, y + titleHeight - RESULT_TITLE_OVERLAP);
                body.setLayoutSize(bodyWidth, bodyHeight);
                body.layout(bodyWidth, bodyHeight);
            }
            height = body.children().isEmpty() ? titleHeight : titleHeight + bodyHeight - RESULT_TITLE_OVERLAP;
        }
        
        @Override
        protected void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            boolean hovered = isInBounds(mouseX, mouseY);
            body.setSurface(hovered ? WikiSurface.BEDROCK_PANEL_HOVER : WikiSurface.BEDROCK_PANEL);
            titleRow.setSurface(hovered ? WikiSurface.BEDROCK_PANEL_PRESSED : WikiSurface.BEDROCK_PANEL_DARK);
            if (!body.children().isEmpty()) body.render(context, mouseX, mouseY, delta);
            titleRow.render(context, mouseX, mouseY, delta);
        }
        
        @Override
        public boolean handleClick(double mouseX, double mouseY, int button) {
            if (button != 0 || !isInBounds(mouseX, mouseY)) return false;
            pressed = true;
            OracleClient.openScreen(wikiId, target, SearchScreen.this);
            return true;
        }
        
        @Override
        public boolean handleMouseRelease(double mouseX, double mouseY, int button) {
            if (button == 0 && pressed) {
                pressed = false;
                return true;
            }
            return false;
        }
        
        private int bodyWidth(int rowWidth) {
            return Math.max(80, rowWidth - RESULT_BODY_INSET);
        }
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
                  Identifier.fromNamespaceAndPath(Oracle.MOD_ID, "expression"), "minecraft:comparator"));
            }
        } catch (RuntimeException ignored) {
        }
        return Optional.empty();
    }
    
    @Override
    public void onClose() {
        Objects.requireNonNull(minecraft).setScreen(parent);
    }
}
