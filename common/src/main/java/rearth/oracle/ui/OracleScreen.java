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
    
    private final Screen parent;
    private final Stack<Identifier> navigationHistory = new Stack<>();
    
    private FlowWidget leftPanel;
    private FlowWidget navigationBar;
    private FlowWidget contentContainer;
    private ScrollWidget leftScroll;
    private ScrollWidget contentScroll;
    private FlowWidget actionHub;
    private ButtonWidget backAction;
    
    private boolean inHistory = false;
    
    public OracleScreen() { this(null); }
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
        navigationBar.setPadding(Insets.of(9, 5, 5, 5));
        
        leftScroll = new ScrollWidget(navigationBar);
        leftScroll.scrollSpeed(15);
        // surface frames the viewport (stays fixed while content scrolls inside)
        leftScroll.setSurface(WikiSurface.BEDROCK_PANEL_DARK);
        
        leftPanel = FlowWidget.vertical().gap(4);
        leftPanel.horizontalAlignment(FlowWidget.HorizontalAlignment.CENTER);
        leftPanel.child(buildWikiTitleHeader());
        leftPanel.child(leftScroll);
        
        // content area
        contentContainer = FlowWidget.vertical().gap(4);
        contentContainer.setPadding(Insets.of(20, 25, 0, 0));
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
            try { loadContent(activeEntry, activeWiki); }
            catch (IOException e) { Oracle.LOGGER.error("Failed to reload active entry: {}", e.getMessage()); }
        }
    }
    
    private FlowWidget buildWikiTitleHeader() {
        // Picks active wiki and shows it; clicking cycles through loaded wikis.
        var wikiIds = OracleClient.LOADED_WIKIS.stream().sorted().toList();
        if (wikiIds.isEmpty()) return FlowWidget.horizontal();
        if (activeWiki == null) activeWiki = wikiIds.get(0);
        
        var wrapper = FlowWidget.horizontal();
        wrapper.setSurface(WikiSurface.BEDROCK_PANEL);
        wrapper.setPadding(Insets.of(8));
        var label = new LabelWidget(
            Text.translatable(Oracle.MOD_ID + ".title." + activeWiki).formatted(Formatting.DARK_GRAY)
                .append(Text.literal(" >").formatted(Formatting.DARK_GRAY))
        ).scale(1.5f);
        wrapper.child(label);
        wrapper.setZIndex(5);
        
        var titleButton = new ButtonWidget(Text.empty(), b -> {
            int idx = wikiIds.indexOf(activeWiki);
            activeWiki = wikiIds.get((idx + 1) % wikiIds.size());
            activeEntry = null;
            requestLayout();
            // rebuild widget tree on next init; force by re-init
            init(MinecraftClient.getInstance(), this.width, this.height);
        });
        // make the button bound the wrapper area; use an overlay approach: place button after wrapper at same coords
        // simpler — replace the wrapper's surface logic: the wrapper acts as the click target.
        // We pretend the wrapper is the button: attach a click handler via an invisible button child.
        // For brevity, return wrapper plus invisible click button stacked; but FlowWidget doesn't support absolute
        // positioning. So instead: subclass FlowWidget? No — too much code. Use a dedicated holder widget below.
        return new ClickableHolder(wrapper, titleButton);
    }
    
    /**
     * Tiny composite that renders a wrapper and forwards clicks to a button
     * sized to the wrapper's bounds. Used so we can keep the visual wrapper
     * style while making it interactive.
     */
    private static class ClickableHolder extends FlowWidget {
        private final ButtonWidget proxyButton;
        ClickableHolder(UIComponent visual, ButtonWidget proxyButton) {
            super(Direction.HORIZONTAL);
            this.proxyButton = proxyButton;
            child(visual);
        }
        @Override
        public void layout(int parentWidthHint, int parentHeightHint) {
            super.layout(parentWidthHint, parentHeightHint);
            proxyButton.setPosition(getX(), getY());
            proxyButton.setSize(getWidth(), getHeight());
        }
        @Override
        public boolean handleClick(double mx, double my, int button) {
            if (proxyButton.isInBounds(mx, my)) {
                proxyButton.handleClick(mx, my, button);
                return true;
            }
            return super.handleClick(mx, my, button);
        }
    }
    
    // ---------------------------------------------------------------- layout
    
    @Override
    protected void layoutWidgets() {
        int leftOffset = Math.max(15, this.width / 20);
        int actionMargin = 4;
        
        // determine left panel preferred size
        int leftPrefW = leftPanel.getPreferredWidth(-1);
        int leftPrefH = (int) (this.height * 0.95f);
        leftPanel.setSize(Math.min(leftPrefW, this.width / 2), leftPrefH);
        
        // give the inner left scroll an explicit size: remaining height under header
        int headerHeight = (leftPanel.children().isEmpty()) ? 0 : leftPanel.children().get(0).getPreferredHeight(leftPanel.getWidth());
        leftScroll.setSize(leftPanel.getWidth() - 4, leftPrefH - headerHeight - 12);
        
        // content size
        int contentW;
        boolean wideEnough = this.width >= 650;
        // compute tentative wide layout
        int wideContentPx = (int) (this.width * (WIDE_CONTENT_WIDTH_PCT / 100f));
        int wideLeftEdge = this.width / 2 - wideContentPx / 2;
        if (leftOffset + leftPanel.getWidth() + 30 > wideLeftEdge) wideEnough = false;
        
        if (wideEnough) {
            contentW = wideContentPx;
            leftPanel.setPosition(leftOffset, (this.height - leftPrefH) / 2);
            contentScroll.setPosition(wideLeftEdge, (this.height - leftPrefH) / 2);
            contentScroll.setSize(contentW, leftPrefH);
        } else {
            contentW = this.width - leftPanel.getWidth() - leftOffset - 20;
            leftPanel.setPosition(leftOffset, (this.height - leftPrefH) / 2);
            contentScroll.setPosition(leftPanel.getX() + leftPanel.getWidth() + 10, (this.height - leftPrefH) / 2);
            contentScroll.setSize(contentW, leftPrefH);
        }
        
        leftPanel.layout(leftPanel.getWidth(), leftPrefH);
        contentScroll.layout(contentW, leftPrefH);
        
        // action hub bottom-right
        int hubW = actionHub.getPreferredWidth(-1);
        int hubH = actionHub.getPreferredHeight(hubW);
        actionHub.setSize(hubW, hubH);
        actionHub.setPosition(this.width - hubW - actionMargin, this.height - hubH - actionMargin);
        actionHub.setZIndex(50);
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
    
    // ---------------------------------------------------------------- hub button
    
    private ButtonWidget makeHubAction(Text tooltip, Identifier iconTexture, int texW, int texH, int w, int h, Consumer<ButtonWidget> onPress) {
        var button = new ButtonWidget(Text.empty(), onPress);
        button.size(w, h);
        button.withTooltip(tooltip);
        button.renderer((btn, ctx, mx, my, d) -> {
            // surface
            WikiSurface s = !btn.enabled() ? WikiSurface.BEDROCK_PANEL_DISABLED
                : btn.isHovered(mx, my) ? WikiSurface.BEDROCK_PANEL_HOVER : WikiSurface.BEDROCK_PANEL;
            s.render(ctx, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight());
            int ix = btn.getX() + (btn.getWidth() - texW) / 2 + 1;
            int iy = btn.getY() + (btn.getHeight() - texH) / 2 - 1;
            if (btn.isHovered(mx, my)) iy++;
            ctx.drawTexture(iconTexture, ix, iy, 0, 0, texW, texH, texW, texH);
        });
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
        } catch (IOException e) { Oracle.LOGGER.error("unable to open page from history: {}", e.getMessage()); }
        backAction.setVisible(!navigationHistory.isEmpty());
    }
    
    private int currentContentWidth() {
        if (contentScroll != null && contentScroll.getWidth() > 0) return contentScroll.getWidth();
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
            if (ingameTarget != null) { loadContent(ingameTarget, wikiId); return true; }
            if (link.startsWith("@") || link.contains(":")) {
                var id = link.startsWith("@") ? link.substring(1) : link;
                if (id.startsWith("minecraft") || !id.contains(":")) {
                    var webLink = "https://minecraft.wiki/w/" + id.replace("minecraft:", "");
                    try { return tryOpenWebLink(webLink); } catch (URISyntaxException e) { return false; }
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
        if (canSwitchWikiMode(activeWiki)) {
            navigationBar.child(buildModeSelector());
        }
        var path = activeWikiMode.equals("docs") ? "" : "/.content";
        buildNavigationEntries(activeWiki, path, navigationBar);
    }
    
    private FlowWidget buildModeSelector() {
        var row = FlowWidget.horizontal().gap(2);
        row.horizontalAlignment(FlowWidget.HorizontalAlignment.CENTER);
        row.child(makeModeButton("docs"));
        row.child(makeModeButton("content"));
        return row;
    }
    
    private ButtonWidget makeModeButton(String mode) {
        boolean isInactive = !activeWikiMode.equals(mode);
        var text = Text.translatable("oracle_index.button." + mode).formatted(isInactive ? Formatting.DARK_GRAY : Formatting.WHITE);
        var btn = new ButtonWidget(text, b -> {
            if (!activeWikiMode.equals(mode)) {
                activeWikiMode = mode;
                activeEntry = null;
                buildNavigationTree();
                requestLayout();
            }
        });
        btn.size(60, 16);
        btn.enabled(isInactive); // mirror the prior behaviour: active mode is unclickable
        return btn;
    }
    
    private String getWikiMode(String wikiId) {
        var modes = OracleClient.AVAILABLE_MODES.getOrDefault(wikiId, Set.of("docs"));
        if (modes.contains(activeWikiMode)) return activeWikiMode;
        return modes.stream().findFirst().orElse("docs");
    }
    
    private boolean canSwitchWikiMode(String wikiId) {
        return OracleClient.AVAILABLE_MODES.getOrDefault(wikiId, Set.of("docs")).size() > 1;
    }
    
    /** @return true if any entry under this path is unlocked. */
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
                    
                    var label = new LabelWidget(labelText.copy().formatted(Formatting.UNDERLINE));
                    label.setPadding(Insets.of(3, 2, 5, 2));
                    if (isUnlocked) {
                        anyUnlocked = true;
                        label.linkHandler(s -> {
                            try { loadContent(labelPath, wikiId); return true; }
                            catch (IOException e) { Oracle.LOGGER.error(e.getMessage()); return false; }
                        });
                        // give it a baseline click handler since we have no real link href
                        label.text(labelText.copy());
                    } else {
                        label.text(labelText.copy().formatted(Formatting.OBFUSCATED));
                    }
                    container.child(new NavigationLink(labelPath, wikiId, label, isUnlocked));
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
    
    /** Wraps a label with a click-to-open behaviour for sidebar entries. */
    private class NavigationLink extends FlowWidget {
        private final Identifier target;
        private final String wikiId;
        private final boolean unlocked;
        NavigationLink(Identifier target, String wikiId, LabelWidget label, boolean unlocked) {
            super(Direction.HORIZONTAL);
            this.target = target;
            this.wikiId = wikiId;
            this.unlocked = unlocked;
            child(label);
        }
        @Override
        public boolean handleClick(double mx, double my, int button) {
            if (!unlocked || button != 0 || !isInBounds(mx, my)) return super.handleClick(mx, my, button);
            try { loadContent(target, wikiId); return true; }
            catch (IOException e) { Oracle.LOGGER.error(e.getMessage()); return false; }
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
    
    public record MetaJsonEntry(String id, String name, boolean directory) {}
}
