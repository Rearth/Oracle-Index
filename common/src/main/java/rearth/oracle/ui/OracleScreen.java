package rearth.oracle.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import rearth.oracle.Oracle;
import rearth.oracle.OracleClient;
import rearth.oracle.progress.OracleProgressAPI;
import rearth.oracle.ui.widgets.*;
import rearth.oracle.util.MarkdownParser;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static rearth.oracle.OracleClient.ROOT_DIR;

public class OracleScreen extends WikiBaseScreen {
    
    public static final HashMap<Identifier, String> PAGE_FALLBACK_NAMES = new HashMap<>();
    
    public static String activeWikiMode = "docs";
    public static Identifier activeEntry;
    public static String activeWiki;
    
    private static final int WIDE_CONTENT_WIDTH_PCT = 50;
    private static final int SIDEBAR_WIDTH = 168;
    private static final int MIN_SIDEBAR_WIDTH = 132;
    private static final int NAV_ROW_HEIGHT = 16;
    private static final int WIKI_HEADER_HEIGHT = 25;
    
    private final Screen parent;
    private final Stack<Identifier> navigationHistory = new Stack<>();
    
    private FlowWidget leftPanel;
    private FlowWidget navigationBar;
    private FlowWidget contentContainer;
    private ScrollWidget leftScroll;
    private ScrollWidget contentScroll;
    private FlowWidget actionHub;
    private FlowWidget modDropdown;
    private UIComponent wikiTitleHeader;
    private LabelWidget wikiTitleLabel;
    private ClickableWidget backAction;
    
    private boolean inHistory = false;
    
    public OracleScreen() {
        this(null);
    }
    
    public OracleScreen(Screen parent) {
        super(Text.translatable("oracle_index.title.screen"));
        this.parent = parent;
    }
    
    @Override
    public void close() {
        Objects.requireNonNull(client).setScreen(parent);
    }
    
    public void fullClose() {
        Objects.requireNonNull(client).setScreen(null);
    }
    
    // ---------------------------------------------------------------- build
    
    @Override
    protected void buildRoots() {
        // navigation bar (the inner content of the left scroll)
        navigationBar = FlowWidget.vertical().gap(2);
        navigationBar.setPadding(Insets.of(7, 5, 5, 5));
        
        leftScroll = new ScrollWidget(navigationBar);
        leftScroll.scrollSpeed(15);
        // surface frames the viewport (stays fixed while content scrolls inside)
        leftScroll.setSurface(WikiSurface.BEDROCK_PANEL_DARK);
        
        leftPanel = FlowWidget.vertical().gap(3);
        wikiTitleHeader = buildWikiTitleHeader();
        leftPanel.child(wikiTitleHeader);
        modDropdown = buildModDropdown();
        modDropdown.setVisible(false);
        leftPanel.child(modDropdown);
        leftPanel.child(leftScroll);
        
        // content area
        contentContainer = FlowWidget.vertical().gap(4);
        contentContainer.setPadding(Insets.of(20, 0, 0, 0));
        contentScroll = new ScrollWidget(contentContainer);
        contentScroll.scrollSpeed(20);
        
        // action hub (back / search / close)
        actionHub = FlowWidget.horizontal().gap(2);
        backAction = makeHubAction(Text.translatable("tooltip.oracle_index.back"),
          Identifier.ofVanilla("textures/gui/sprites/widget/page_backward.png"), 23, 13, 24, 20,
          b -> back());
        backAction.setVisible(!navigationHistory.isEmpty());
        var searchAction = makeHubAction(Text.translatable("tooltip.oracle_index.open_search",
            OracleClient.ORACLE_WIKI.getBoundKeyLocalizedText(),
            OracleClient.ORACLE_SEARCH.getBoundKeyLocalizedText()),
          Identifier.ofVanilla("textures/gui/sprites/icon/search.png"), 16, 16, 24, 24,
          b -> MinecraftClient.getInstance().setScreen(new SearchScreen(this)));
        var closeAction = makeHubAction(Text.translatable("tooltip.oracle_index.close_screen"),
          Identifier.ofVanilla("textures/gui/sprites/container/beacon/cancel.png"), 13, 13, 20, 20,
          b -> fullClose());
        actionHub.child(backAction).child(searchAction).child(closeAction);
        
        addRoot(leftPanel);
        addRoot(contentScroll);
        addRoot(actionHub);
        
        buildNavigationTree();
        if (activeEntry != null) {
            try {
                loadContent(activeEntry, activeWiki);
            } catch (IOException e) {
                Oracle.LOGGER.error("Failed to reload active entry: {}", e.getMessage());
            }
        }
    }
    
    private UIComponent buildWikiTitleHeader() {
        var wikiIds = OracleClient.LOADED_WIKIS.stream().sorted().toList();
        if (wikiIds.isEmpty()) return FlowWidget.horizontal();
        if (activeWiki == null || !wikiIds.contains(activeWiki)) activeWiki = wikiIds.get(0);
        
        wikiTitleLabel = new LabelWidget(wikiTitleText().copy().append(Text.literal(" >"))).scale(1.35f);
        
        var header = new ClickableWidget(wikiTitleLabel, b -> {
            if (modDropdown != null) {
                modDropdown.setVisible(!modDropdown.isVisible());
                requestLayout();
            }
        }) {
            @Override
            public int getPreferredHeight(int widthHint) {
                return super.getPreferredHeight(widthHint) - 8;
            }
            
            @Override
            public void render(DrawContext context, int mouseX, int mouseY, float delta) {
                if (!visible) return;
                context.getMatrices().push();
                var offset = leftPanel.getWidth() * 0f - this.getWidth() * 0f;
                context.getMatrices().translate(offset, 0, 10);
                var surface = currentSurface(mouseX, mouseY);
                surface.render(context, x - 5, y, width + 10, height);
                getChild().render(context, mouseX, mouseY, delta);
                context.getMatrices().pop();
            }
        }.fixedHeight(WIKI_HEADER_HEIGHT)
                       .surfaces(WikiSurface.BEDROCK_PANEL, WikiSurface.BEDROCK_PANEL_HOVER,
                         WikiSurface.BEDROCK_PANEL_PRESSED, WikiSurface.BEDROCK_PANEL, WikiSurface.BEDROCK_PANEL_DISABLED);
        header.setPadding(Insets.of(8, 4, 4, 8));
        header.centerChild();
        return header;
    }
    
    private Text wikiTitleText() {
        return Text.translatable(Oracle.MOD_ID + ".title." + activeWiki).formatted(Formatting.DARK_GRAY);
    }
    
    private FlowWidget buildModDropdown() {
        var dropdown = FlowWidget.vertical().gap(1);
        dropdown.setSurface(WikiSurface.BEDROCK_PANEL_DARK);
        dropdown.setPadding(Insets.of(3));
        rebuildModDropdown(dropdown);
        return dropdown;
    }
    
    private void rebuildModDropdown(FlowWidget dropdown) {
        dropdown.clearChildren();
        var wikiIds = OracleClient.LOADED_WIKIS.stream().sorted().toList();
        for (var wikiId : wikiIds) {
            var label = new LabelWidget(Text.translatable(Oracle.MOD_ID + ".title." + wikiId).formatted(wikiId.equals(activeWiki) ? Formatting.WHITE : Formatting.DARK_GRAY));
            label.setPadding(Insets.of(4, 3));
            var row = new ClickableWidget(label, b -> selectWiki(wikiId))
                        .fillWidth()
                        .selected(wikiId.equals(activeWiki))
                        .surfaces(WikiSurface.BEDROCK_PANEL, WikiSurface.BEDROCK_PANEL_HOVER, WikiSurface.BEDROCK_PANEL_PRESSED,
                          WikiSurface.BEDROCK_PANEL, WikiSurface.BEDROCK_PANEL_DISABLED);
            row.setPadding(Insets.of(7, 5));
            row.enabled(!wikiId.equals(activeWiki));
            dropdown.child(row);
        }
    }
    
    private void selectWiki(String wikiId) {
        if (modDropdown != null) modDropdown.setVisible(false);
        if (!wikiId.equals(activeWiki)) {
            activeWiki = wikiId;
            activeWikiMode = getWikiMode(activeWiki);
            activeEntry = null;
            navigationHistory.clear();
            if (backAction != null) backAction.setVisible(false);
            if (contentContainer != null) contentContainer.clearChildren();
            if (wikiTitleLabel != null) wikiTitleLabel.text(wikiTitleText().copy().append(Text.literal(" >")));
            if (modDropdown != null) rebuildModDropdown(modDropdown);
            buildNavigationTree();
        }
        requestLayout();
    }
    
    // ---------------------------------------------------------------- layout
    
    @Override
    protected void layoutWidgets() {
        int leftOffset = Math.max(15, this.width / 20);
        int actionMargin = 4;
        int sidebarWidth = Math.max(MIN_SIDEBAR_WIDTH, Math.min(SIDEBAR_WIDTH, this.width / 3));
        
        int leftPrefH = (int) (this.height * 0.95f);
        leftPanel.setLayoutSize(sidebarWidth, leftPrefH);
        
        // give the inner left scroll an explicit size: remaining height under header
        int headerHeight = wikiTitleHeader == null ? 0 : wikiTitleHeader.getPreferredHeight(sidebarWidth);
        int dropdownHeight = modDropdown != null && modDropdown.isVisible() ? modDropdown.getPreferredHeight(sidebarWidth) : 0;
        int visibleTopRows = modDropdown != null && modDropdown.isVisible() ? 2 : 1;
        int usedGap = leftPanel.gap() * visibleTopRows;
        leftScroll.setLayoutSize(sidebarWidth, Math.max(40, leftPrefH - headerHeight - dropdownHeight - usedGap));
        
        int panelY = (this.height - leftPrefH) / 2;
        leftPanel.setPosition(leftOffset, panelY);
        
        int contentAreaLeft = leftOffset + sidebarWidth + 10;
        int contentAreaRight = this.width - leftOffset;
        int contentAreaWidth = Math.max(120, contentAreaRight - contentAreaLeft);
        int desiredContentWidth = (int) (this.width * (WIDE_CONTENT_WIDTH_PCT / 100f));
        int contentW;
        int contentX;
        if (this.width >= 650) {
            contentW = Math.min(desiredContentWidth, contentAreaWidth);
            contentX = (int) (contentAreaLeft + Math.max(0, (contentAreaWidth - contentW) / 2) * 0.8);
        } else {
            contentW = contentAreaWidth;
            contentX = contentAreaLeft;
        }
        contentScroll.setPosition(contentX, panelY);
        contentScroll.setLayoutSize(contentW, leftPrefH);
        
        leftPanel.layout(leftPanel.getWidth(), leftPrefH);
        contentScroll.layout(contentW, leftPrefH);
        
        // action hub bottom-right
        int hubW = actionHub.getPreferredWidth(-1);
        int hubH = actionHub.getPreferredHeight(hubW);
        actionHub.setLayoutSize(hubW, hubH);
        actionHub.setPosition(this.width - hubW - actionMargin, this.height - hubH - actionMargin);
        actionHub.layout(hubW, hubH);
    }
    
    // ---------------------------------------------------------------- render
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (Screen.hasControlDown()) {
            Oracle.LOGGER.info("Opening Oracle Search...");
            Objects.requireNonNull(client).setScreen(new SearchScreen(this));
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isModDropdownOpen()
              && !modDropdown.isInBounds(mouseX, mouseY)
              && (wikiTitleHeader == null || !wikiTitleHeader.isInBounds(mouseX, mouseY))) {
            closeModDropdown();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && isModDropdownOpen()) {
            closeModDropdown();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    private boolean isModDropdownOpen() {
        return modDropdown != null && modDropdown.isVisible();
    }
    
    private void closeModDropdown() {
        modDropdown.setVisible(false);
        requestLayout();
    }
    
    // ---------------------------------------------------------------- hub button
    
    private ClickableWidget makeHubAction(Text tooltip, Identifier iconTexture, int texW, int texH, int w, int h, Consumer<ClickableWidget> onPress) {
        var icon = new TextureWidget(iconTexture, texW, texH);
        var button = new ClickableWidget(icon, onPress)
                       .fixedSize(w, h)
                       .centerChild()
                       .surfaces(WikiSurface.BEDROCK_PANEL, WikiSurface.BEDROCK_PANEL_HOVER,
                         WikiSurface.BEDROCK_PANEL_PRESSED, WikiSurface.BEDROCK_PANEL, WikiSurface.BEDROCK_PANEL_DISABLED);
        button.withTooltip(tooltip);
        return button;
    }
    
    // ---------------------------------------------------------------- nav
    
    private void back() {
        if (navigationHistory.isEmpty()) return;
        var target = navigationHistory.pop();
        try {
            inHistory = true;
            loadContent(target, activeWiki);
            inHistory = false;
        } catch (IOException e) {
            Oracle.LOGGER.error("unable to open page from history: {}", e.getMessage());
        }
        backAction.setVisible(!navigationHistory.isEmpty());
    }
    
    private int currentContentWidth() {
        if (contentScroll != null && contentScroll.getWidth() > 0) {
            int padding = contentContainer != null ? contentContainer.getPadding().horizontal() : 0;
            return Math.max(80, contentScroll.contentWidth() - padding);
        }
        if (this.width >= 650) return (int) (this.width * (WIDE_CONTENT_WIDTH_PCT / 100f));
        return Math.max(200, this.width / 2);
    }
    
    private void loadContent(Identifier filePath, String wikiId) throws IOException {
        var lastEntry = activeEntry;
        contentContainer.clearChildren();
        activeEntry = filePath;
        
        var translatedPath = OracleClient.getTranslatedPath(filePath, wikiId);
        if (translatedPath.isPresent()) filePath = translatedPath.get();
        
        var rm = MinecraftClient.getInstance().getResourceManager();
        var rc = rm.getResource(filePath);
        if (rc.isEmpty()) {
            Oracle.LOGGER.warn("No content file found for {}", filePath);
            return;
        }
        
        if (lastEntry != null && lastEntry != activeEntry && !inHistory && (navigationHistory.isEmpty() || !navigationHistory.peek().equals(lastEntry)))
            navigationHistory.push(lastEntry);
        if (backAction != null) backAction.setVisible(!navigationHistory.isEmpty());
        
        var fileContent = new String(rc.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        final var finalPath = filePath;
        var widgets = MarkdownParser.parseMarkdownToWidgets(fileContent, wikiId, filePath,
          link -> onLinkClicked(wikiId, link, finalPath), currentContentWidth());
        for (var w : widgets) {
            if (w != null) contentContainer.child(w);
        }
        contentScroll.scrollTo(0);
        requestLayout();
    }
    
    private boolean onLinkClicked(String wikiId, String link, Identifier sourceEntryPath) {
        try {
            if (link.startsWith("https")) return tryOpenWebLink(link);
            var ingameTarget = MarkdownParser.getLinkTarget(link, wikiId, sourceEntryPath);
            if (ingameTarget != null) {
                loadContent(ingameTarget, wikiId);
                return true;
            }
            if (link.startsWith("@") || link.contains(":")) {
                var id = link.startsWith("@") ? link.substring(1) : link;
                if (id.startsWith("minecraft") || !id.contains(":")) {
                    var webLink = "https://minecraft.wiki/w/" + id.replace("minecraft:", "");
                    try {
                        return tryOpenWebLink(webLink);
                    } catch (URISyntaxException e) {
                        return false;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            Oracle.LOGGER.error("Oracle Index: Could not find/open link {}", link);
            Oracle.LOGGER.error(e.getMessage());
            return false;
        }
    }
    
    private boolean tryOpenWebLink(String link) throws URISyntaxException {
        var uri = new URI(link);
        var confirm = new ConfirmLinkScreen(accepted -> {
            if (accepted) Util.getOperatingSystem().open(uri);
            MinecraftClient.getInstance().setScreen(this);
        }, link, true);
        MinecraftClient.getInstance().setScreen(confirm);
        return true;
    }
    
    // ---------------------------------------------------------------- nav tree
    
    private void buildNavigationTree() {
        navigationBar.clearChildren();
        activeWikiMode = getWikiMode(activeWiki);
        if (canSwitchWikiMode(activeWiki)) {
            navigationBar.child(buildModeSelector());
        }
        var path = activeWikiMode.equals("docs") ? "" : "/.content";
        buildNavigationEntries(activeWiki, path, navigationBar);
    }
    
    private FlowWidget buildModeSelector() {
        var row = FlowWidget.horizontal().gap(-1);
        row.size(SIDEBAR_WIDTH - 10, 0);
        row.horizontalAlignment(FlowWidget.HorizontalAlignment.CENTER);
        row.child(makeModeButton("docs"));
        row.child(makeModeButton("content"));
        return row;
    }
    
    private ClickableWidget makeModeButton(String mode) {
        boolean selected = activeWikiMode.equals(mode);
        var text = Text.translatable("oracle_index.button." + mode).formatted(selected ? Formatting.WHITE : Formatting.DARK_GRAY);
        var label = new LabelWidget(text);
        var widget = new ClickableWidget(label, b -> {
            if (!selected) {
                activeWikiMode = mode;
                activeEntry = null;
                navigationHistory.clear();
                if (backAction != null) backAction.setVisible(false);
                contentContainer.clearChildren();
                leftScroll.scrollTo(0);
                buildNavigationTree();
                requestLayout();
            }
        }).centerChild().selected(selected)
                 .surfaces(WikiSurface.BEDROCK_PANEL, WikiSurface.BEDROCK_PANEL_HOVER,
                   WikiSurface.BEDROCK_PANEL_PRESSED, WikiSurface.BEDROCK_PANEL_PRESSED, WikiSurface.BEDROCK_PANEL_DISABLED);
        
        widget.setPadding(Insets.of(5, 12));
        
        return widget;
    }
    
    private String getWikiMode(String wikiId) {
        var modes = OracleClient.AVAILABLE_MODES.getOrDefault(wikiId, Set.of("docs"));
        if (modes.contains(activeWikiMode)) return activeWikiMode;
        if (modes.contains("docs")) return "docs";
        if (modes.contains("content")) return "content";
        return modes.stream().findFirst().orElse("docs");
    }
    
    private boolean canSwitchWikiMode(String wikiId) {
        return OracleClient.AVAILABLE_MODES.getOrDefault(wikiId, Set.of("docs")).size() > 1;
    }
    
    /**
     * @return true if any entry under this path is unlocked.
     */
    private boolean buildNavigationEntries(String wikiId, String path, FlowWidget container) {
        var rm = MinecraftClient.getInstance().getResourceManager();
        var metaPath = Identifier.of(Oracle.MOD_ID, ROOT_DIR + "/" + wikiId + path + "/_meta.json");
        var translated = OracleClient.getTranslatedPath(metaPath, wikiId);
        if (translated.isPresent()) metaPath = translated.get();
        
        var rc = rm.getResource(metaPath);
        if (rc.isEmpty()) {
            Oracle.LOGGER.warn("No _meta.json found for {} at {}", wikiId, metaPath);
            return false;
        }
        
        boolean anyUnlocked = false;
        try {
            var meta = new String(rc.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var entries = parseJson(meta);
            
            var levelContainers = new ArrayList<CollapsibleWidget>();
            
            for (var entry : entries) {
                if (entry.directory) {
                    var directory = new CollapsibleWidget(Text.translatable(entry.name()).formatted(Formatting.WHITE), false);
                    boolean childrenUnlocked = buildNavigationEntries(wikiId, path + "/" + entry.id(), directory.body());
                    if (childrenUnlocked) anyUnlocked = true;
                    
                    final var captured = directory;
                    directory.onToggle(c -> {
                        // collapse all sibling collapsibles when this one is toggled
                        for (var other : levelContainers) {
                            if (other != captured && other.expanded()) other.setExpanded(false);
                        }
                        requestLayout();
                    });
                    
                    if (childrenUnlocked) {
                        container.child(directory);
                        levelContainers.add(directory);
                    }
                } else {
                    final var labelPath = Identifier.of(Oracle.MOD_ID, ROOT_DIR + "/" + wikiId + path + "/" + entry.id());
                    
                    var shownName = entry.name;
                    if (shownName.isBlank()) {
                        var contentRc = rm.getResource(labelPath);
                        if (contentRc.isEmpty()) {
                            Oracle.LOGGER.warn("Unable to get name for entry: {}", labelPath);
                            shownName = "<ERROR>";
                        } else {
                            var fileContent = new String(contentRc.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                            var fm = MarkdownParser.parseFrontmatter(fileContent);
                            shownName = MarkdownParser.getTitle(fm, labelPath);
                        }
                    }
                    PAGE_FALLBACK_NAMES.put(labelPath, shownName);
                    
                    final var labelText = Text.translatable(shownName).formatted(Formatting.WHITE);
                    boolean isUnlocked = true;
                    if (OracleClient.UNLOCK_CRITERIONS.containsKey(labelPath.getPath())) {
                        var unlockData = OracleClient.UNLOCK_CRITERIONS.get(labelPath.getPath());
                        isUnlocked = OracleProgressAPI.IsUnlocked(wikiId, labelPath.getPath(), unlockData.getLeft(), unlockData.getRight());
                    }
                    
                    var label = new LabelWidget(labelText.copy());
                    if (isUnlocked) {
                        anyUnlocked = true;
                        label.text(labelText.copy());
                    } else {
                        label.text(labelText.copy().formatted(Formatting.OBFUSCATED));
                    }
                    container.child(new NavigationLink(labelPath, wikiId, label, labelText, isUnlocked));
                }
            }
            
            if (activeEntry == null) {
                var first = entries.stream().filter(e -> !e.directory).findFirst();
                if (first.isPresent()) {
                    var firstPath = Identifier.of(Oracle.MOD_ID, ROOT_DIR + "/" + wikiId + path + "/" + first.get().id());
                    loadContent(firstPath, wikiId);
                    activeEntry = firstPath;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return anyUnlocked;
    }
    
    /**
     * Wraps a label with click, hover, and current-page state for sidebar entries.
     */
    private class NavigationLink extends ClickableWidget {
        private final Identifier target;
        private final LabelWidget label;
        private final Text labelText;
        private final boolean unlocked;
        private boolean wasSelected;
        private boolean wasHovered;
        
        NavigationLink(Identifier target, String wikiId, LabelWidget label, Text labelText, boolean unlocked) {
            super(label, b -> {
                try {
                    loadContent(target, wikiId);
                } catch (IOException e) {
                    Oracle.LOGGER.error(e.getMessage());
                }
            });
            this.target = target;
            this.label = label;
            this.labelText = labelText;
            this.unlocked = unlocked;
            fillWidth().enabled(unlocked)
              .surfaces(WikiSurface.NONE, WikiSurface.NONE, WikiSurface.NONE, WikiSurface.NONE, WikiSurface.NONE);
            setPadding(Insets.of(2, 5));
            updateText(false, false);
        }
        
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean selected = target.equals(activeEntry);
            boolean hovered = isInBounds(mouseX, mouseY);
            if (selected != wasSelected || hovered != wasHovered) updateText(selected, hovered);
            super.render(context, mouseX, mouseY, delta);
        }
        
        private void updateText(boolean selected, boolean hovered) {
            wasSelected = selected;
            wasHovered = hovered;
            if (!unlocked) {
                label.text(labelText.copy().formatted(Formatting.OBFUSCATED));
            } else if (selected && hovered) {
                label.text(labelText.copy().formatted(Formatting.UNDERLINE, Formatting.GRAY));
            } else if (selected) {
                label.text(labelText.copy().formatted(Formatting.UNDERLINE));
            } else if (hovered) {
                label.text(labelText.copy().formatted(Formatting.GRAY));
            } else {
                label.text(labelText.copy());
            }
        }
    }
    
    // ---------------------------------------------------------------- meta json
    
    private static List<MetaJsonEntry> parseJson(String jsonString) {
        var gson = new Gson();
        var jsonObject = gson.fromJson(jsonString, JsonObject.class);
        var entries = new ArrayList<MetaJsonEntry>();
        for (var entry : jsonObject.entrySet()) {
            var id = entry.getKey();
            var value = entry.getValue();
            String name;
            boolean directory = !id.endsWith(".mdx");
            if (value instanceof JsonPrimitive) name = value.getAsString();
            else if (value instanceof JsonObject) name = ((JsonObject) value).get("name").getAsString();
            else name = "Unknown Name";
            entries.add(new MetaJsonEntry(id, name, directory));
        }
        return entries;
    }
    
    public static @NotNull String parsePathLink(String link, Identifier sourceEntryPath) {
        var cleanLink = link.split("#")[0];
        var currentPathObj = Path.of(sourceEntryPath.getPath());
        var currentParentDir = currentPathObj.getParent();
        if (currentParentDir == null) currentParentDir = Path.of("");
        var resolved = currentParentDir.resolve(cleanLink).normalize();
        return resolved.toString().replace("\\", "/");
    }
    
    public record MetaJsonEntry(String id, String name, boolean directory) {
    }
}
