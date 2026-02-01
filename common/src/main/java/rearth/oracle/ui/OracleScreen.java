package rearth.oracle.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.wispforest.owo.ui.base.BaseOwoScreen;
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
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import rearth.oracle.Oracle;
import rearth.oracle.OracleClient;
import rearth.oracle.progress.OracleProgressAPI;
import rearth.oracle.ui.components.ColoredCollapsibleContainer;
import rearth.oracle.ui.components.ScalableLabelComponent;
import rearth.oracle.util.MarkdownParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OracleScreen extends BaseOwoScreen<FlowLayout> {
    
    private FlowLayout navigationBar;
    private FlowLayout contentContainer;
    private FlowLayout rootComponent;
    private FlowLayout leftPanel;
    private ScrollContainer<FlowLayout> outerContentContainer;
    
    private final Screen parent;
    
    private boolean needsLayout = false;
    
    public static Identifier activeEntry;
    public static String activeBook;
    
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
    
    private void loadContentContainer(Identifier filePath, String bookId) throws IOException {
        
        contentContainer.clearChildren();
        activeEntry = filePath;
        
        var translatedPath = OracleClient.getTranslatedPath(filePath, bookId);
        if (translatedPath.isPresent())
            filePath = translatedPath.get();
        
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var resourceCandidate = resourceManager.getResource(filePath);
        
        if (resourceCandidate.isEmpty()) {
            System.out.println("No content file found for " + filePath);
            return;
        }
        
        var fileContent = new String(resourceCandidate.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        final var finalFilePath = filePath;
        var parsedTexts = MarkdownParser.parseMarkdownToOwoComponents(fileContent, bookId, link -> onLinkClicked(bookId, link, finalFilePath));
        
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
    
    // returns true if the link has been handled / is valid
    private boolean onLinkClicked(String bookId, String link, Identifier sourceEntryPath) {
        
        // links can either point to the internet, or to another entry in the wiki
        if (link.startsWith("http")) {
            return tryOpenWebLink(link);
        } else {
            return tryOpenWikiLink(bookId, link, sourceEntryPath);
        }
    }
    
    private boolean tryOpenWikiLink(String bookId, String link, Identifier sourceEntryPath) {
        var newPathString = parsePathLink(link, sourceEntryPath);
        
        // add extension if missing. Theoretically links without file ending would be valid/used in some cases
        if (!newPathString.endsWith(".mdx")) {
            newPathString += ".mdx";
        }
        
        var newId = Identifier.of(Oracle.MOD_ID, newPathString);
        
        try {
            loadContentContainer(newId, bookId);
            return true;
        } catch (IOException e) {
            Oracle.LOGGER.warn("Oracle: Could not find/open linked page " + newId);
            Oracle.LOGGER.warn(e.getMessage());
            return false;
        }
    }
    
    private boolean tryOpenWebLink(String link) {
        try {
            final var uri = new URI(link);
            
            // minecraft-typical confirmation screen
            var confirmScreen = new ConfirmLinkScreen((accepted) -> {
                if (accepted) {
                    Util.getOperatingSystem().open(uri);
                }
                // Return to this OracleScreen after the user decides
                MinecraftClient.getInstance().setScreen(this);
            }, link, true);
            
            MinecraftClient.getInstance().setScreen(confirmScreen);
            return true;
        } catch (Exception e) {
            Oracle.LOGGER.error("Failed to open link: {}", link, e);
            return false;
        }
    }
    
    private @NotNull String parsePathLink(String link, Identifier sourceEntryPath) {
        
        // anchors inside the page are not supported
        var cleanLink = link.split("#")[0];
        
        var currentPathObj = Path.of(sourceEntryPath.getPath());
        var currentParentDir = currentPathObj.getParent();
        
        if (currentParentDir == null) {
            currentParentDir = Path.of("");
        }
        
        // this should resolve "../" and the sorts automatically
        var resolvedPath = currentParentDir.resolve(cleanLink).normalize();
        
        // convert back to string and ensure forward slashes (might only be needed on windows?)
        return resolvedPath.toString().replace("\\", "/");
    }
    
    private void buildModNavigation(FlowLayout buttonContainer) {
        
        // collect all book ids
        var bookIds = OracleClient.LOADED_BOOKS.stream()
                        .sorted()
                        .toList();
        
        var modSelectorDropdown = Components.dropdown(Sizing.content(3));
        modSelectorDropdown.zIndex(5);
        
        if (activeBook == null)
            activeBook = bookIds.getFirst();
        
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
        
        var bookTitleLabel = new ScalableLabelComponent(Text.translatable(Oracle.MOD_ID + ".title." + activeBook).formatted(Formatting.DARK_GRAY).append(" >").formatted(Formatting.DARK_GRAY), text -> false);
        bookTitleLabel.scale = 1.5f;
        var bookTitleWrapper = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        bookTitleWrapper.surface(MarkdownParser.ORACLE_PANEL);
        bookTitleWrapper.padding(Insets.of(8));
        bookTitleWrapper.margins(Insets.of(topMargins, -7, 0, 0));
        bookTitleWrapper.child(bookTitleLabel);
        buttonContainer.child(bookTitleWrapper.zIndex(5));
        
        bookTitleWrapper.mouseEnter().subscribe(() -> {
            bookTitleWrapper.surface(MarkdownParser.ORACLE_PANEL_HOVER);
        });
        bookTitleWrapper.mouseLeave().subscribe(() -> {
            bookTitleWrapper.surface(MarkdownParser.ORACLE_PANEL);
        });
        bookTitleWrapper.mouseDown().subscribe((a, b, c) -> {
            if (modSelectorDropdown.hasParent()) {
                modSelectorDropdown.remove();
                return true;
            }
            rootComponent.child(modSelectorDropdown.positioning(Positioning.absolute(bookTitleWrapper.x() + bookTitleWrapper.width(), bookTitleWrapper.y())));
            return true;
        });
        
        for (var bookId : bookIds) {
            modSelectorDropdown.button(Text.translatable(Oracle.MOD_ID + ".title." + bookId), elem -> {
                activeEntry = null;
                modSelectorDropdown.remove();
                buildModNavigationBar(bookId);
                bookTitleLabel.text(Text.translatable(Oracle.MOD_ID + ".title." + bookId).formatted(Formatting.DARK_GRAY).append(" >").formatted(Formatting.DARK_GRAY));
                activeBook = bookId;
            });
        }
        
        buildModNavigationBar(activeBook);
        
    }
    
    private void buildModNavigationBar(String bookId) {
        navigationBar.clearChildren();
        buildNavigationEntriesForModPath(bookId, "", navigationBar);
    }
    
    // returns whether any children are unlocked (e.g. only false if all children are locked)
    private boolean buildNavigationEntriesForModPath(String bookId, String path, FlowLayout container) {
        
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var metaPath = Identifier.of(Oracle.MOD_ID, "books/" + bookId + path + "/_meta.json");
        
        var translatedMetaPath = OracleClient.getTranslatedPath(metaPath, bookId);
        if (translatedMetaPath.isPresent()) {
            metaPath = translatedMetaPath.get();
        }
        
        var resourceCandidate = resourceManager.getResource(metaPath);
        
        if (resourceCandidate.isEmpty()) {
            System.out.println("No _meta.json found for " + bookId + " at " + metaPath);
            return false;
        }
        
        var anyUnlocked = false;
        
        try {
            var metaFile = new String(resourceCandidate.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var entries = parseJson(metaFile);
            
            if (activeEntry == null) {
                var firstEntry = entries.stream().filter(elem -> !elem.directory).findFirst();
                if (firstEntry.isPresent()) {
                    var firstEntryPath = Identifier.of(Oracle.MOD_ID, "books/" + bookId + path + "/" + firstEntry.get().id());
                    loadContentContainer(firstEntryPath, bookId);
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
                    var anyChildrenUnlocked = buildNavigationEntriesForModPath(bookId, path + "/" + entry.id(), directoryContainer);
                    if (anyChildrenUnlocked) anyUnlocked = true;
                    directoryContainer.margins(Insets.of(0, 0, 0, 0));
                    
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
                    
                    if (anyChildrenUnlocked) {
                        container.child(directoryContainer);
                        levelContainers.add(directoryContainer);
                    }
                    
                    
                } else {
                    final var labelPath = Identifier.of(Oracle.MOD_ID, "books/" + bookId + path + "/" + entry.id());
                    final var labelText = Text.translatable(entry.name).formatted(Formatting.WHITE);
                    final var label = Components.label(labelText.formatted(Formatting.UNDERLINE));
                    
                    var isUnlocked = true;
                    if (OracleClient.UNLOCK_CRITERIONS.containsKey(labelPath.getPath())) {
                        var unlockData = OracleClient.UNLOCK_CRITERIONS.get(labelPath.getPath());
                        isUnlocked = OracleProgressAPI.IsUnlocked(bookId, labelPath.getPath(), unlockData.getLeft(), unlockData.getRight());
                    }
                    
                    if (isUnlocked) {
                        anyUnlocked = true;
                        label.mouseEnter().subscribe(() -> {
                            label.text(labelText.copy().formatted(Formatting.GRAY));
                        });
                        label.mouseLeave().subscribe(() -> {
                            label.text(labelText.copy());
                        });
                        
                        label.mouseDown().subscribe((a, b, c) -> {
                            try {
                                loadContentContainer(labelPath, bookId);
                                return true;
                            } catch (IOException e) {
                                Oracle.LOGGER.error(e.getMessage());
                                return false;
                            }
                        });
                    } else {
                        label.text(labelText.formatted(Formatting.OBFUSCATED));
                    }
                    
                    label.margins(Insets.of(3, 2, 5, 2));
                    container.child(label);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        return anyUnlocked;
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
