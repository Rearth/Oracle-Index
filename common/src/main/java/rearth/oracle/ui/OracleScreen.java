package rearth.oracle.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.base.BaseParentComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextureComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import rearth.oracle.Oracle;
import rearth.oracle.OracleClient;
import rearth.oracle.ui.components.ColoredCollapsibleContainer;
import rearth.oracle.ui.components.ScalableLabelComponent;
import rearth.oracle.util.BookMetadata;
import rearth.oracle.util.MarkdownParser;
import rearth.oracle.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OracleScreen extends BaseOwoScreen<FlowLayout> {
    
    private FlowLayout navigationBar;
    private FlowLayout contentContainer;
    private FlowLayout rootComponent;
    private FlowLayout leftPanel;
    private ScrollContainer<FlowLayout> outerContentContainer;
    private BaseParentComponent langSelector;
    
    private final Screen parent;
    
    private boolean needsLayout = false;
    
    public static Identifier activeEntry;
    public static BookMetadata activeBook;
    
    private static final int wideContentWidth = 50; // in %
    
    public OracleScreen() {
        this.parent = null;
    }
    
    public OracleScreen(Screen parent) {
        this.parent = parent;
    }
    
    @Override
    public void close() {
        Objects.requireNonNull(client).setScreen(parent);
    }
    
    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::horizontalFlow);
    }
    
    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.blur(4f, 48f));
        rootComponent.child(Components.box(Sizing.fill(), Sizing.fill()).color(new Color(0.1f, 0.1f, 0.15f, 0.9f)).fill(true).zIndex(-1).positioning(Positioning.absolute(0, 0)));
        rootComponent.horizontalAlignment(HorizontalAlignment.LEFT);
        rootComponent.verticalAlignment(VerticalAlignment.CENTER);
        this.rootComponent = rootComponent;
        
        leftPanel = Containers.verticalFlow(Sizing.content(), Sizing.fill());
        leftPanel.horizontalAlignment(HorizontalAlignment.CENTER);
        
        navigationBar = Containers.verticalFlow(Sizing.content(), Sizing.content(3));
        navigationBar.surface(MarkdownParser.ORACLE_PANEL_DARK);
        navigationBar.padding(Insets.of(9, 5, 5, 5));
        rootComponent.child(leftPanel);
        
        contentContainer = Containers.verticalFlow(Sizing.fill(), Sizing.content(3));
        contentContainer.horizontalSizing(Sizing.fill());
        contentContainer.horizontalAlignment(HorizontalAlignment.CENTER);
        contentContainer.margins(Insets.of(2, 2, 4, 4));
        contentContainer.padding(Insets.of(20, 25, 0, 0));
        contentContainer.allowOverflow(true);
        
        outerContentContainer = Containers.verticalScroll(Sizing.fill(wideContentWidth), Sizing.fill(), contentContainer);
        outerContentContainer.allowOverflow(true);
        rootComponent.child(outerContentContainer);

        buildModNavigation(leftPanel);
        
        var outerNavigationBarContainer = Containers.verticalScroll(Sizing.content(3), Sizing.fill(80), navigationBar);
        leftPanel.child(outerNavigationBarContainer);
        
        // search icon
        var searchContainer = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        searchContainer.surface(MarkdownParser.ORACLE_PANEL);
        searchContainer.margins(Insets.of(6, 6, 6, 6));
        searchContainer.padding(Insets.of(4, 6, 4, 4));
        searchContainer.positioning(Positioning.relative(99, 99));
        
        searchContainer.mouseDown().subscribe(((mouseX, mouseY, button) -> {
            MinecraftClient.getInstance().setScreen(new SearchScreen(this));
            return true;
        }));
        
        searchContainer.mouseEnter().subscribe(() -> searchContainer.surface(MarkdownParser.ORACLE_PANEL_HOVER));
        searchContainer.mouseLeave().subscribe(() -> searchContainer.surface(MarkdownParser.ORACLE_PANEL));
        
        var searchIcon = Components.item(new ItemStack(Items.SPYGLASS));
        searchIcon.sizing(Sizing.fixed(24));
        searchIcon.tooltip(Text.translatable("tooltip.oracle_index.open_search", OracleClient.ORACLE_WIKI.getBoundKeyLocalizedText(), OracleClient.ORACLE_SEARCH.getBoundKeyLocalizedText()));
        
        searchContainer.child(searchIcon);
        
        rootComponent.child(searchContainer.zIndex(5));
    }
    
    @Override
    protected void init() {
        super.init();
        
        updateLayout();
    }
    
    private void updateLayout() {
        var leftOffset = Math.max(15, this.width / 20);
        var leftPanelSize = leftPanel.width();
        var leftPanelEnd = leftPanel.x() + leftPanel.width();
        var innerPanelWideLeft = this.width * 0.5f - this.width * wideContentWidth / 100f / 2f;
        
        var wideEnough = this.width >= 650;
        if (leftPanelEnd > innerPanelWideLeft + 30)
            wideEnough = false;
        
        // "responsive" layout
        if (wideEnough) {
            rootComponent.horizontalAlignment(HorizontalAlignment.CENTER);
            leftPanel.positioning(Positioning.relative(0, 0));
            leftPanel.margins(Insets.of(0, 0, leftOffset, leftOffset / 2));
            outerContentContainer.positioning(Positioning.relative(60, 50));
            outerContentContainer.horizontalSizing(Sizing.fill(wideContentWidth));
        } else {
            rootComponent.horizontalAlignment(HorizontalAlignment.LEFT);
            leftPanel.positioning(Positioning.layout());
            outerContentContainer.positioning(Positioning.layout());
            leftPanel.margins(Insets.of(0, 0, 10, 5));
            outerContentContainer.horizontalSizing(Sizing.fixed(this.width - leftPanelSize - 20));
        }

        langSelector.positioning(Positioning.absolute(this.width - 120, 10))
                .margins(Insets.top(10).withRight(5));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        if (needsLayout) {
            needsLayout = false;
            this.updateLayout();
        }
        
        if (Screen.hasControlDown()) {
            Oracle.LOGGER.info("Opening Oracle Search...");
            Objects.requireNonNull(client).setScreen(new SearchScreen(this));
        }
    }
    
    private void loadContentContainer(Identifier filePath, BookMetadata bookMetadata) throws IOException {
        
        contentContainer.clearChildren();
        activeEntry = filePath;
        
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var resourceCandidate = resourceManager.getResource(filePath);
        
        if (resourceCandidate.isEmpty()) {
            Oracle.LOGGER.warn("No content file found for {}", filePath);
            return;
        }
        
        var fileContent = new String(resourceCandidate.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var parsedTexts = MarkdownParser.parseMarkdownToOwoComponents(fileContent, bookMetadata.getBookId(), link -> {
            
            if (link.startsWith("http")) return false;
            
            var pathSegments = filePath.getPath().split("/");
            StringBuilder newPathBuilder = new StringBuilder();
            
            // build path based on relative information
            var parentIteration = link.startsWith("../") ? 1 : 0;
            for (int i = 0; i < pathSegments.length - 1 - parentIteration; i++) {
                newPathBuilder.append(pathSegments[i]).append("/");
            }
            
            newPathBuilder = new StringBuilder(newPathBuilder.toString().split("#")[0]);    // anchors are not supported, so we just remove them
            newPathBuilder.append(link.replace("../", "")).append(".mdx");    // add file ending

            var newPath = newPathBuilder.toString();

            Oracle.LOGGER.info("Loading content file: " + newPath);

            var newId = Identifier.of(Oracle.MOD_ID, newPath);
            
            try {
                loadContentContainer(newId, bookMetadata);
            } catch (IOException e) {
                return false;
            }
            return true;
        });
        
        for (var paragraph : parsedTexts) {
            
            if (paragraph instanceof LabelComponent) {
                paragraph.horizontalSizing(Sizing.fill());
            } else if (paragraph instanceof TextureComponent textureComponent) {
                var ratio = textureComponent.visibleArea().get().width() / (float) textureComponent.visibleArea().get().height();
                var targetSize = textureComponent.verticalSizing().get().value / 100f;
                var maxWidth = this.width * 0.6f;
                var usedWidth = maxWidth * targetSize * 0.8f;
                var height = usedWidth / ratio;
                
                textureComponent.sizing(Sizing.fixed((int) usedWidth), Sizing.fixed((int) height));
            } else if (paragraph instanceof ItemComponent itemComponent) {
                var ratio = 1f;
                var targetSize = itemComponent.verticalSizing().get().value / 100f;
                var maxWidth = this.width * 0.6f;
                var usedWidth = maxWidth * targetSize * 0.8f;
                var height = usedWidth / ratio;
                
                itemComponent.sizing(Sizing.fixed((int) usedWidth), Sizing.fixed((int) height));
            }
            
            if (paragraph.margins().get().equals(Insets.of(0)))
                paragraph.margins(Insets.of(4, 1, 0, 0));
            contentContainer.child(paragraph);
        }
    }
    
    private void buildModNavigation(FlowLayout buttonContainer) {

        // collect all book metadata
        var bookMetadataList = OracleClient.LOADED_BOOKS.values().stream()
                        .sorted()
                        .toList();
        
        var modSelectorDropdown = Components.dropdown(Sizing.content(3));
        modSelectorDropdown.zIndex(5);
        
        if (activeBook == null)
            activeBook = bookMetadataList.getFirst();
        
        if (activeEntry != null) {
            try {
                loadContentContainer(activeEntry, activeBook);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        var topMargins = 40;
        if (this.height < 350) {
            topMargins = 5;
        }

        buildLangSelector();
        
        var bookTitleLabel = new ScalableLabelComponent(Text.translatable(Oracle.MOD_ID + ".title." + activeBook).formatted(Formatting.DARK_GRAY).append(" >").formatted(Formatting.DARK_GRAY), text -> false);
        bookTitleLabel.scale = 1.5f;
        var bookTitleWrapper = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        bookTitleWrapper.surface(MarkdownParser.ORACLE_PANEL);
        bookTitleWrapper.padding(Insets.of(8));
        bookTitleWrapper.margins(Insets.of(topMargins, -7, 0, 0));
        bookTitleWrapper.child(bookTitleLabel);
        buttonContainer.child(bookTitleWrapper.zIndex(5));
        
        bookTitleWrapper.mouseEnter().subscribe(() -> bookTitleWrapper.surface(MarkdownParser.ORACLE_PANEL_HOVER));
        bookTitleWrapper.mouseLeave().subscribe(() -> bookTitleWrapper.surface(MarkdownParser.ORACLE_PANEL));
        bookTitleWrapper.mouseDown().subscribe((a, b, c) -> {
            if (modSelectorDropdown.hasParent()) {
                modSelectorDropdown.remove();
                return true;
            }
            rootComponent.child(modSelectorDropdown.positioning(Positioning.absolute(bookTitleWrapper.x() + bookTitleWrapper.width(), bookTitleWrapper.y())));
            return true;
        });
        
        for (var bookMetadata : bookMetadataList) {
            var bookId = bookMetadata.getBookId();
            modSelectorDropdown.button(Text.translatable(Oracle.MOD_ID + ".title." + bookId), elem -> {
                activeBook = bookMetadata;
                activeEntry = null;
                modSelectorDropdown.remove();
                rootComponent.removeChild(langSelector);
                buildLangSelector();
                buildModNavigationBar(bookMetadata);
                bookTitleLabel.text(Text.translatable(Oracle.MOD_ID + ".title." + bookId).formatted(Formatting.DARK_GRAY).append(" >").formatted(Formatting.DARK_GRAY));
            });
        }
        
        buildModNavigationBar(activeBook);
        
    }

    private void buildLangSelector() {
        var langLabel = Components.label(Text.translatable("oracle_index.label.lang"))
                .color(Color.ofFormatting(Formatting.WHITE))
                .margins(Insets.right(5));

        var currentLangText = Components.label(
                Util.getLanguageText(activeBook.getCurrentLanguage()).copy().formatted(Formatting.DARK_GRAY)
        );

        var currentLangTextWrapper = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        currentLangTextWrapper.surface(MarkdownParser.ORACLE_PANEL);
        currentLangTextWrapper.mouseEnter().subscribe(() -> currentLangTextWrapper.surface(MarkdownParser.ORACLE_PANEL_HOVER));
        currentLangTextWrapper.mouseLeave().subscribe(() -> currentLangTextWrapper.surface(MarkdownParser.ORACLE_PANEL));
        currentLangTextWrapper.child(currentLangText.margins(Insets.of(3, 5, 3, 5)));

        var langLabelWrapper = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        langLabelWrapper.child(langLabel);
        langLabelWrapper.child(currentLangTextWrapper);

        var langDropdown = Components.dropdown(Sizing.content());

        // add supported languages
        for (String lang : activeBook.getSupportedLanguages()) {
            langDropdown.button(Util.getLanguageText(lang).copy().formatted(Formatting.WHITE), dropdownComponent -> {
                activeBook.setCurrentLanguage(lang);
                currentLangText.text(Util.getLanguageText(lang).copy().formatted(Formatting.DARK_GRAY));
                langDropdown.remove();

                // reload entries and content
                if (activeEntry != null) {
                    try {
                        Oracle.LOGGER.info("Reloading content for " + activeEntry.getPath());
                        activeEntry = Identifier.of(Oracle.MOD_ID, activeBook.convertPathToCurrentLanguage(activeEntry.getPath()));
                        buildModNavigationBar(activeBook);
                        loadContentContainer(activeEntry, activeBook);
                    } catch (IOException e) {
                        Oracle.LOGGER.error("Failed to reload content: " + e.getMessage());
                    }
                }
            }).zIndex(10);
        }

        // show dropdown
        langLabelWrapper.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (langDropdown.hasParent()) {
                langDropdown.remove();
                return true;
            }

            langDropdown.positioning(Positioning.absolute(langLabelWrapper.x(), langLabelWrapper.y() + langLabelWrapper.height()));
            rootComponent.child(langDropdown);
            return true;
        });

        langSelector = langLabelWrapper
                .positioning(Positioning.absolute(this.width - 120, 10))
                .margins(Insets.top(10).withRight(5));
        rootComponent.child(langSelector);
    }
    
    private void buildModNavigationBar(BookMetadata bookMetadata) {
        navigationBar.clearChildren();
        buildNavigationEntriesForModPath(bookMetadata, "", navigationBar);
    }
    
    private void buildNavigationEntriesForModPath(BookMetadata bookMetadata, String path, FlowLayout container) {
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var metaPath = Identifier.of(Oracle.MOD_ID, bookMetadata.getEntryPath(path) + "/_meta.json");
        var resourceCandidate = resourceManager.getResource(metaPath);
        
        if (resourceCandidate.isEmpty()) {
            System.out.println("No _meta.json found for " + bookMetadata.getBookId() + " at " + metaPath);
            return;
        }
        
        try {
            var metaFile = new String(resourceCandidate.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var entries = parseJson(metaFile);
            
            if (activeEntry == null) {
                var firstEntry = entries.stream().filter(elem -> !elem.directory).findFirst();
                if (firstEntry.isPresent()) {
                    // Oracle.LOGGER.info(bookMetadata.getEntryPath(path) + firstEntry.get().id());
                    var firstEntryPath = Identifier.of(Oracle.MOD_ID, bookMetadata.getEntryPath(path) + "/" + firstEntry.get().id());
                    loadContentContainer(firstEntryPath, bookMetadata);
                    activeEntry = firstEntryPath;
                }
            }
            
            var levelContainers = new ArrayList<ColoredCollapsibleContainer>();
            
            for (var entry : entries) {
                if (entry.directory) {
                    var directoryContainer = new ColoredCollapsibleContainer(
                      Sizing.content(1),
                      Sizing.content(1),
                      Text.translatable(entry.name()).formatted(Formatting.WHITE), false);
                    buildNavigationEntriesForModPath(bookMetadata, path + "/" + entry.id(), directoryContainer);
                    directoryContainer.margins(Insets.of(0, 0, 0, 0));
                    container.child(directoryContainer);
                    
                    // collapse all other containers
                    directoryContainer.mouseDown().subscribe((a, b, c) -> {
                        for (var elem : levelContainers) {
                            if (elem == directoryContainer) continue;
                            if (elem.expanded()) {
                                elem.toggleExpansion();
                            }
                        }
                        this.needsLayout = true;
                        return false;
                    });
                    
                    levelContainers.add(directoryContainer);
                    
                } else {
                    final var labelPath = Identifier.of(Oracle.MOD_ID, bookMetadata.getEntryPath(path) + "/" + entry.id());
                    final var labelText = Text.translatable(entry.name).formatted(Formatting.WHITE);
                    final var label = Components.label(labelText.formatted(Formatting.UNDERLINE));
                    
                    label.mouseEnter().subscribe(() -> label.text(labelText.copy().formatted(Formatting.GRAY)));
                    label.mouseLeave().subscribe(() -> label.text(labelText.copy()));
                    
                    label.mouseDown().subscribe((a, b, c) -> {
                        try {
                            loadContentContainer(labelPath, bookMetadata);
                            return true;
                        } catch (IOException e) {
                            Oracle.LOGGER.error(e.getMessage(), e);
                            return false;
                        }
                    });
                    
                    label.margins(Insets.of(3, 2, 5, 2));
                    container.child(label);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static List<MetaJsonEntry> parseJson(String jsonString) {
        var gson = new Gson();
        var jsonObject = gson.fromJson(jsonString, JsonObject.class);
        var entries = new ArrayList<MetaJsonEntry>();
        
        for (var entry : jsonObject.entrySet()) {
            var id = entry.getKey();
            var value = entry.getValue();
            String name;
            boolean directory;
            
            directory = !id.endsWith(".mdx");
            
            if (value instanceof JsonPrimitive) {
                name = value.getAsString();
            } else if (value instanceof JsonObject) {
                name = ((JsonObject) value).get("name").getAsString();
            } else {
                name = "Unknown Name"; // Fallback, should not happen
            }
            
            entries.add(new MetaJsonEntry(id, name, directory));
        }
        return entries;
        
    }
    
    public record MetaJsonEntry(String id, String name, boolean directory) {
        @Override
        public String toString() {
            return "MetaJsonEntry{" +
                     "id='" + id + '\'' +
                     ", name='" + name + '\'' +
                     ", directory=" + directory +
                     '}';
        }
    }
}
