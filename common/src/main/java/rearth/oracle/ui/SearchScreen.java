package rearth.oracle.ui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.base.BaseParentComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.jetbrains.annotations.NotNull;
import rearth.oracle.Oracle;
import rearth.oracle.OracleClient;
import rearth.oracle.SemanticSearch;
import rearth.oracle.ui.components.ScalableLabelComponent;
import rearth.oracle.util.MarkdownParser;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SearchScreen extends BaseOwoScreen<FlowLayout> {
    
    private FlowLayout resultsPanel;
    private int waitFrames = 0;
    
    private final Screen parent;
    
    // Regular expression: allows digits, whitespace, and math operators
    private static final String MATH_EXPR_REGEX = "^[\\d\\s+\\-*/%().]+$";
    private TextBoxComponent searchBar;
    
    
    public SearchScreen(Screen parent) {
        this.parent = parent;
    }
    
    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }
    
    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.blur(4f, 24f));
        rootComponent.child(Components.box(Sizing.fill(), Sizing.fill()).color(new Color(0.1f, 0.1f, 0.15f, 0.6f)).fill(true).zIndex(-1).positioning(Positioning.absolute(0, 0)));
        rootComponent.horizontalAlignment(HorizontalAlignment.CENTER);
        rootComponent.verticalAlignment(VerticalAlignment.CENTER);
        
        var mainContainer = Containers.verticalFlow(Sizing.fixed(350), Sizing.fill(99));
        rootComponent.child(mainContainer);
        
        var searchPanel = Containers.horizontalFlow(Sizing.fill(), Sizing.content(2));
        searchPanel.margins(Insets.of(16, -2, 0, 0));
        
        var oracleIcon = Components.texture(Identifier.of(Oracle.MOD_ID, "textures/oracle-index-icon.png"), 0, 0, 256, 256, 256, 256);
        oracleIcon.sizing(Sizing.fixed(50));
        oracleIcon.zIndex(2);
        searchPanel.child(oracleIcon);
        
        searchBar = Components.textBox(Sizing.fixed(350 - 40 - 14), "");
        searchBar.onChanged().subscribe(this::onSearchTyped);
        searchBar.tooltip(Text.translatable("oracle_index.searchbar.tooltip"));
        searchBar.setSuggestion(Text.translatable("oracle_index.searchbar.placeholder").formatted(Formatting.GRAY, Formatting.ITALIC).getString());
        searchBar.setEditable(false);
        
        var searchTextContainer = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        searchTextContainer.surface(MarkdownParser.ORACLE_PANEL);
        searchTextContainer.padding(Insets.of(3, 5, 22, 3));
        searchTextContainer.margins(Insets.of(4, 0, -25, 0));
        searchTextContainer.child(searchBar);
        
        searchPanel.child(searchTextContainer);
        
        mainContainer.child(searchPanel);
        
        resultsPanel = Containers.verticalFlow(Sizing.expand(), Sizing.content(3));
        
        var outerContainer = Containers.verticalScroll(Sizing.fill(), Sizing.fill(80), resultsPanel);
        mainContainer.child(outerContainer);
        
    }
    
    @Override
    protected void init() {
        super.init();
        this.uiAdapter.rootComponent.focusHandler().focus(searchBar, Component.FocusSource.MOUSE_CLICK);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        var searchReady = false;
        try {
            searchReady = OracleClient.getOrCreateSearch().isReady();
        } catch (Throwable error) {
            error.printStackTrace();
            
            MinecraftClient.getInstance().player.sendMessage(Text.literal("Sorry, Oracle Index Search is not available on your platform."));
            MinecraftClient.getInstance().player.sendMessage(Text.literal("If you want this search feature, you can use the xplat jar available on the mods github."));
            MinecraftClient.getInstance().player.sendMessage(Text.literal("https://github.com/Rearth/Oracle-Index - The jars are available in the 'releases' section."));
            MinecraftClient.getInstance().player.sendMessage(Text.literal("This is not the default file due to jar size limitations."));
            
            this.close();
            return;
        }
        
        if (searchReady) {
            searchBar.setEditable(true);
            if (searchBar.getText().startsWith("Indexing")) {
                searchBar.setText("");
                Oracle.LOGGER.info("Embeddings took {}ms across all threads.", OracleClient.getOrCreateSearch().getEmbeddingTime() / 1_000_000);
            }
        } else {
            waitFrames++;
            var dots = (waitFrames / 2) % 3 + 1;
            searchBar.setEditable(false);
            searchBar.setText("Indexing" + ".".repeat(dots));
            searchBar.setSuggestion("");
        }
    }
    
    @SuppressWarnings("OptionalIsPresent")
    private void onSearchTyped(String query) {
        
        if (query.startsWith("Indexing")) return;
        
        var usedPlaceholder = Text.translatable("oracle_index.searchbar.placeholder").formatted(Formatting.GRAY, Formatting.ITALIC).getString();
        if (!query.isEmpty()) usedPlaceholder = "";
        searchBar.setSuggestion(usedPlaceholder);
        
        if (query.length() <= 2) return;
        
        List<SemanticSearch.SearchResult> results;
        var expressionResult = tryProcessExpression(query);
        
        // abort early if partial match expression
        if (expressionResult.isEmpty() && query.matches(MATH_EXPR_REGEX)) {
            return;
        }
        
        resultsPanel.clearChildren();
        
        if (expressionResult.isPresent()) {
            results = List.of(expressionResult.get());
        } else {
            results = OracleClient.getOrCreateSearch().search(query);
        }
        
        for (var result : results) {
            
            var resultTitlePanel = Containers.horizontalFlow(Sizing.content(), Sizing.content());
            resultTitlePanel.surface(MarkdownParser.ORACLE_PANEL_DARK);
            resultTitlePanel.margins(Insets.of(2, 2, 1, 5));
            resultTitlePanel.padding(Insets.of(6, 7, 6, 8));
            resultTitlePanel.zIndex(2);
            
            if (result.iconName() != null && Registries.ITEM.containsId(Identifier.of(result.iconName()))) {
                var itemDisplay = new ItemStack(Registries.ITEM.get(Identifier.of(result.iconName())));
                var itemComponent = Components.item(itemDisplay);
                itemComponent.sizing(Sizing.fixed(14));
                itemComponent.margins(Insets.of(-3, -2, -2, 4));
                resultTitlePanel.allowOverflow(true);
                resultTitlePanel.child(itemComponent);
            }
            
            // var score = (int) (result.bestScore() * 100);
            // var titleText = Text.literal(result.title() + " (" + score + "%)");  // variant with score in title
            var titleText = Text.literal(result.title());
            var title = Components.label(titleText.formatted(Formatting.BOLD));
            resultTitlePanel.child(title);
            
            var resultTextPanel = Containers.verticalFlow(Sizing.fill(), Sizing.content());
            resultTextPanel.surface(MarkdownParser.ORACLE_PANEL);
            resultTextPanel.margins(Insets.of(-5, 2, 10, 5));
            resultTextPanel.padding(Insets.of(7, 8, 4, 4));
            
            // process first non-empty paragraph
            for (var text : result.texts()) {
                var paragraphs = MarkdownParser.parseMarkdownToOwoComponents(text, result.id().getNamespace(), string -> false);
                
                if (paragraphs.getFirst() instanceof BaseParentComponent parentComponent)
                    paragraphs.addAll(parentComponent.children());
                
                var hadContent = false;
                for (var paragraph : paragraphs) {
                    // load only text paragraphs, skip all other kinds
                    if (paragraph instanceof ScalableLabelComponent labelComponent && labelComponent.scale == 1) {
                        paragraph.horizontalSizing(Sizing.fill());
                        paragraph.margins(Insets.of(0));
                        labelComponent.lineSpacing(0);
                        resultTextPanel.child(paragraph);
                        labelComponent.text(labelComponent.text().copy().formatted(Formatting.DARK_GRAY));
                        hadContent = true;
                    }
                }
                if (hadContent)
                    break;  // stop if we have a valid paragraph (needed if the first result only had an image for example).
            }
            
            resultsPanel.child(resultTitlePanel);
            
            if (!resultTextPanel.children().isEmpty())
                resultsPanel.child(resultTextPanel);
            
            resultTextPanel.mouseEnter().subscribe(() -> {
                resultTextPanel.surface(MarkdownParser.ORACLE_PANEL_HOVER);
            });
            resultTextPanel.mouseLeave().subscribe(() -> {
                resultTextPanel.surface(MarkdownParser.ORACLE_PANEL);
            });
            
            resultTitlePanel.mouseEnter().subscribe(() -> {
                resultTitlePanel.surface(MarkdownParser.ORACLE_PANEL_PRESSED);
            });
            resultTitlePanel.mouseLeave().subscribe(() -> {
                resultTitlePanel.surface(MarkdownParser.ORACLE_PANEL_DARK);
            });
            
            resultTitlePanel.mouseDown().subscribe(((mouseX, mouseY, button) -> {
                var contentId = Identifier.of(Oracle.MOD_ID, String.format("books/%s/%s", result.id().getNamespace(), result.id().getPath()));
                OracleClient.openScreen(result.id().getNamespace(), contentId, this);
                return true;
            }));
            resultTextPanel.mouseDown().subscribe(((mouseX, mouseY, button) -> {
                var contentId = Identifier.of(Oracle.MOD_ID, String.format("books/%s/%s", result.id().getNamespace(), result.id().getPath()));
                OracleClient.openScreen(result.id().getNamespace(), contentId, this);
                return true;
            }));
            
        }
    }
    
    private Optional<SemanticSearch.SearchResult> tryProcessExpression(String input) {
        
        try {
            var expression = new ExpressionBuilder(input).build();
            var valid = expression.validate();
            
            if (valid.isValid()) {
                var resultingNumber = expression.evaluate();
                var calculationText = String.format("%s = **%s**", input.replace("*", "x"), new DecimalFormat("#.####").format(resultingNumber));
                var res = new SemanticSearch.SearchResult(List.of(calculationText), 1, "Calculation: ", Identifier.of(Oracle.MOD_ID, "expression"), "minecraft:comparator");
                return Optional.of(res);
            } else {
                return Optional.empty();
            }
            
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        
    }
    
    @Override
    public void close() {
        Objects.requireNonNull(client).setScreen(parent);
    }
}
