package rearth.oracle.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.jetbrains.annotations.Nullable;
import rearth.oracle.Oracle;
import rearth.oracle.OracleClient;
import rearth.oracle.ui.OracleScreen;
import rearth.oracle.ui.widgets.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

import static rearth.oracle.OracleClient.ROOT_DIR;

/**
 * Markdown / MDX → wiki widget tree converter.
 * Replaces the previous owo-lib-based parser.
 */
public class MarkdownParser {
    
    private static final String[] removedLines = {"<center>", "</center>", "<div>", "</div>", "<span>", "</span>"};
    
    private static final List<Extension> EXTENSIONS = List.of(YamlFrontMatterExtension.create());
    private static final Set<Class<? extends Block>> ENABLED_BLOCKS = Set.of(
      Heading.class, HtmlBlock.class, ThematicBreak.class,
      FencedCodeBlock.class, BlockQuote.class, ListBlock.class
    );
    
    private static final Parser PARSER = Parser.builder()
                                           .enabledBlockTypes(ENABLED_BLOCKS)
                                           .extensions(EXTENSIONS)
                                           .customBlockParserFactory(new MdxBlockFactory())
                                           .build();
    
    /**
     * Parse markdown and produce a list of top-level widgets.
     *
     * @param contentWidthPx the pixel width of the content viewport — used to
     *                       size images and lay out wrapped labels.
     */
    public static List<UIComponent> parseMarkdownToWidgets(String markdown, String wikiId, Identifier currentPath,
                                                            Predicate<String> linkHandler, int contentWidthPx) {
        for (var toRemove : removedLines) markdown = markdown.replace(toRemove, "");
        
        var document = PARSER.parse(markdown);
        var yamlVisitor = new YamlFrontMatterVisitor();
        document.accept(yamlVisitor);
        
        var frontMatter = parseFrontmatter(markdown);
        
        var visitor = new WikiMarkdownVisitor(linkHandler, wikiId, currentPath, contentWidthPx);
        document.accept(visitor);
        
        var widgets = new ArrayList<UIComponent>();
        widgets.add(buildTitlePanel(linkHandler, frontMatter, currentPath, contentWidthPx));
        widgets.addAll(visitor.results());
        
        if (frontMatter.containsKey("id")) {
            var gameId = frontMatter.get("id");
            var id = Identifier.of(gameId);
            if (Registries.ITEM.containsId(id) || Registries.BLOCK.containsId(id))
                widgets.add(buildPropertiesPanel(ContentProperties.getProperties(gameId), contentWidthPx));
        }
        
        return widgets;
    }
    
    // ---------------------------------------------------------------- visitor
    
    private static class WikiMarkdownVisitor extends AbstractVisitor {
        
        private final Predicate<String> linkHandler;
        private final String wikiId;
        private final Identifier contentPath;
        private final int contentWidthPx;
        
        private List<UIComponent> components = new ArrayList<>();
        private MutableText buffer = Text.empty();
        private Style currentStyle = Style.EMPTY;
        private int currentIndentation = 0;
        
        WikiMarkdownVisitor(Predicate<String> linkHandler, String wikiId, Identifier contentPath, int contentWidthPx) {
            this.linkHandler = linkHandler;
            this.wikiId = wikiId;
            this.contentPath = contentPath;
            this.contentWidthPx = contentWidthPx;
        }
        
        List<UIComponent> results() { return components; }
        
        private void flushBuffer() {
            if (buffer == null || buffer.getString().isEmpty()) return;
            var label = new LabelWidget(buffer).linkHandler(linkHandler).lineSpacing(1).fillWidth();
            label.setPadding(Insets.of(0, 0, currentIndentation * 6, 0));
            components.add(label);
            buffer = Text.empty();
            currentIndentation = 0;
        }
        
        @Override
        public void visit(Paragraph paragraph) {
            visitChildren(paragraph);
            flushBuffer();
        }
        
        @Override
        public void visit(Heading heading) {
            buffer = Text.empty();
            var oldStyle = currentStyle;
            currentStyle = currentStyle.withColor(Formatting.GRAY);
            visitChildren(heading);
            currentStyle = oldStyle;
            
            var label = new LabelWidget(buffer).linkHandler(linkHandler).fillWidth();
            label.scale(Math.max(1.0f, 2.0f - heading.getLevel() * 0.2f));
            label.setPadding(Insets.of(10, 5, 0, 0));
            components.add(label);
            buffer = Text.empty();
        }
        
        @Override
        public void visit(FencedCodeBlock codeBlock) {
            flushBuffer();
            var panel = FlowWidget.vertical();
            panel.setSurface(WikiSurface.BEDROCK_PANEL_DARK);
            panel.setPadding(Insets.of(6));
            var text = Text.literal(codeBlock.getLiteral()).formatted(Formatting.GRAY);
            panel.child(new LabelWidget(text));
            components.add(panel);
        }
        
        @Override public void visit(BulletList l)  { visitChildren(l); }
        @Override public void visit(OrderedList l) { visitChildren(l); }
        
        @Override
        public void visit(ListItem listItem) {
            var parent = listItem.getParent();
            int depth = 0;
            var ancestor = parent;
            while (ancestor instanceof ListBlock || ancestor instanceof ListItem) {
                if (ancestor instanceof ListBlock) depth++;
                ancestor = ancestor.getParent();
            }
            this.currentIndentation = depth - 1;
            
            if (parent instanceof BulletList) {
                buffer.append(Text.literal("• ").formatted(Formatting.DARK_GRAY));
            } else if (parent instanceof OrderedList orderedList) {
                int index = 1;
                var sibling = listItem.getPrevious();
                while (sibling != null) {
                    if (sibling instanceof ListItem) index++;
                    sibling = sibling.getPrevious();
                }
                int n = orderedList.getStartNumber() + index - 1;
                buffer.append(Text.literal(n + ". ").formatted(Formatting.DARK_GRAY));
            }
            visitChildren(listItem);
            flushBuffer();
            this.currentIndentation = 0;
        }
        
        @Override
        public void visit(CustomBlock customBlock) {
            if (customBlock instanceof MdxComponentBlock.CraftingRecipeBlock recipe) {
                components.add(buildRecipe(recipe.slots, recipe.result, recipe.count));
            } else if (customBlock instanceof MdxComponentBlock.AssetBlock image) {
                components.add(buildImage(image.location, image.width, this.wikiId, image.isModAsset(), contentWidthPx));
            } else if (customBlock instanceof MdxComponentBlock.CalloutBlock callout) {
                var oldComponents = this.components;
                var inner = new ArrayList<UIComponent>();
                this.components = inner;
                visitChildren(callout);
                flushBuffer();
                this.components = oldComponents;
                
                var widget = new CalloutWidget(callout.variant);
                for (var c : inner) widget.addBodyChild(c);
                components.add(widget);
            }
        }
        
        @Override
        public void visit(Image image) {
            flushBuffer();
            components.add(buildImage(image.getDestination(), "60%", wikiId, true, contentWidthPx));
        }
        
        @Override
        public void visit(org.commonmark.node.Text text) {
            if (buffer != null) buffer.append(Text.literal(text.getLiteral()).setStyle(currentStyle));
        }
        
        @Override
        public void visit(StrongEmphasis e) {
            var old = currentStyle;
            currentStyle = currentStyle.withBold(true);
            visitChildren(e);
            currentStyle = old;
        }
        
        @Override
        public void visit(Emphasis e) {
            var old = currentStyle;
            currentStyle = currentStyle.withItalic(true);
            visitChildren(e);
            currentStyle = old;
        }
        
        @Override
        public void visit(Link link) {
            var old = currentStyle;
            var clickEvent = new ClickEvent(ClickEvent.Action.OPEN_URL, link.getDestination());
            currentStyle = currentStyle.withColor(Formatting.BLUE).withUnderline(true).withClickEvent(clickEvent);
            
            if (link.getFirstChild() == null && (link.getTitle() == null || link.getTitle().isBlank())) {
                var linkTitle = getLinkText(link.getDestination(), wikiId, contentPath);
                buffer.append(Text.literal(linkTitle).setStyle(currentStyle));
            }
            visitChildren(link);
            currentStyle = old;
        }
        
        @Override
        public void visit(Code inlineCode) {
            if (buffer != null) {
                buffer.append(Text.literal(inlineCode.getLiteral()).formatted(Formatting.RED));
            }
        }
        
        @Override public void visit(SoftLineBreak n) { if (buffer != null) buffer.append(Text.literal(" ")); }
        @Override public void visit(HardLineBreak n) { if (buffer != null) buffer.append(Text.literal("\n")); }
    }
    
    // ---------------------------------------------------------------- helpers
    
    public static String getLinkText(String link, String activeWikiId, Identifier sourceEntryPath) {
        var linkTarget = getLinkTarget(link, activeWikiId, sourceEntryPath);
        if (linkTarget == null) return "<invalid link>";
        var rm = MinecraftClient.getInstance().getResourceManager();
        var rc = rm.getResource(linkTarget);
        if (rc.isEmpty()) return "<invalid link>";
        try {
            var fileContent = new String(rc.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var fm = parseFrontmatter(fileContent);
            return getTitle(fm, linkTarget);
        } catch (IOException e) {
            Oracle.LOGGER.warn("Unable to load file content to get link title: {}, {}", linkTarget, e);
            return "<invalid link>";
        }
    }
    
    @Nullable
    public static Identifier getLinkTarget(String link, String activeWikiId, Identifier sourceEntryPath) {
        Identifier targetFile;
        if (link.startsWith("@") || link.contains(":")) {
            var id = link.startsWith("@") ? link.substring(1) : link;
            targetFile = OracleClient.CONTENT_ID_MAP.get(id);
        } else if (link.startsWith("$")) {
            var p = "books/" + activeWikiId + "/" + link.substring(1);
            if (!p.endsWith(".mdx")) p += ".mdx";
            targetFile = Identifier.of(Oracle.MOD_ID, p);
        } else {
            var p = OracleScreen.parsePathLink(link, sourceEntryPath);
            if (!p.endsWith(".mdx")) p += ".mdx";
            targetFile = Identifier.of(Oracle.MOD_ID, p);
        }
        return targetFile;
    }
    
    public static String getTitle(Map<String, String> frontMatter, Identifier pagePath) {
        if (frontMatter.containsKey("title")) return frontMatter.get("title");
        if (frontMatter.containsKey("id")) {
            var item = frontMatter.get("id");
            if (Identifier.validate(item).isSuccess() && Registries.ITEM.containsId(Identifier.of(item))) {
                return I18n.translate(Registries.ITEM.get(Identifier.of(item)).getTranslationKey());
            }
            return item;
        }
        return OracleScreen.PAGE_FALLBACK_NAMES.getOrDefault(pagePath, "No title found");
    }
    
    private static UIComponent buildTitlePanel(Predicate<String> linkHandler, Map<String, String> frontMatter, Identifier pageId, int contentWidthPx) {
        var row = FlowWidget.horizontal().gap(10);
        row.setSurface(WikiSurface.BEDROCK_PANEL);
        row.setPadding(Insets.of(8, 10));
        row.verticalAlignment(FlowWidget.VerticalAlignment.CENTER);
        
        var iconId = frontMatter.getOrDefault("icon", "");
        if (iconId.isBlank()) iconId = frontMatter.getOrDefault("id", "");
        if (Identifier.validate(iconId).isSuccess() && Registries.ITEM.containsId(Identifier.of(iconId))) {
            var iconStack = new ItemStack(Registries.ITEM.get(Identifier.of(iconId)));
            var icon = new ItemWidget(iconStack);
            icon.size(48, 48);
            row.child(icon);
        }
        
        var titleStr = getTitle(frontMatter, pageId);
        var titleLabel = new LabelWidget(Text.literal(titleStr).formatted(Formatting.DARK_GRAY))
            .scale(2f)
            .wrapWidth(Math.max(80, contentWidthPx - 90))
            .linkHandler(linkHandler);
        row.child(titleLabel);
        
        return row;
    }
    
    private static UIComponent buildPropertiesPanel(Map<String, Text> properties, int contentWidthPx) {
        var tr = MinecraftClient.getInstance().textRenderer;
        int titleWidth = tr.getWidth("Details");
        int keyWidth = 0;
        int valueWidth = 0;
        for (var entry : properties.entrySet()) {
            keyWidth = Math.max(keyWidth, tr.getWidth(entry.getKey()));
            valueWidth = Math.max(valueWidth, tr.getWidth(entry.getValue()));
        }
        int innerWidth = Math.min(contentWidthPx, Math.max(160, Math.max(titleWidth, keyWidth + valueWidth + 28) + 20));
        var outer = FlowWidget.vertical().gap(2);
        outer.setSurface(WikiSurface.BEDROCK_PANEL_DARK);
        outer.setPadding(Insets.of(10));
        outer.size(innerWidth, 0);
        outer.horizontalAlignment(FlowWidget.HorizontalAlignment.CENTER);
        outer.child(new LabelWidget(Text.literal("Details").formatted(Formatting.BOLD, Formatting.GRAY)));
        
        for (var entry : properties.entrySet()) {
            outer.child(new PropertyRowWidget(Text.literal(entry.getKey()).formatted(Formatting.GOLD), entry.getValue()));
        }
        return outer;
    }
    
    private static class PropertyRowWidget extends UIComponent {
        private final Text key;
        private final Text value;
        
        PropertyRowWidget(Text key, Text value) {
            this.key = key;
            this.value = value;
        }
        
        @Override
        public int getPreferredWidth(int widthHint) {
            return widthHint > 0 ? widthHint : MinecraftClient.getInstance().textRenderer.getWidth(key) + 28 + MinecraftClient.getInstance().textRenderer.getWidth(value);
        }
        
        @Override
        public int getPreferredHeight(int widthHint) {
            return MinecraftClient.getInstance().textRenderer.fontHeight;
        }
        
        @Override
        protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
            var tr = MinecraftClient.getInstance().textRenderer;
            context.drawText(tr, key, x, y, 0xFFFFFFFF, false);
            context.drawText(tr, value, x + width - tr.getWidth(value), y, 0xFFFFFFFF, false);
        }
    }
    
    public static UIComponent buildRecipe(List<String> inputs, String resultId, int resultCount) {
        if (inputs.size() != 9) {
            return new LabelWidget(Text.literal("Invalid crafting recipe data: expected 9 inputs").formatted(Formatting.RED));
        }
        
        // Layered: a 3x3 grid of slots with items overlaid on top.
        var grid = new GridWidget(3, 3, ItemSlotWidget.SLOT_SIZE, ItemSlotWidget.SLOT_SIZE).gap(0, 0);
        grid.setPadding(Insets.of(3));
        for (int i = 0; i < 9; i++) {
            var input = inputs.get(i);
            ItemStack stack = ItemStack.EMPTY;
            if (!input.isEmpty() && !input.equals("minecraft:air")) {
                var id = Identifier.of(input);
                if (Registries.ITEM.containsId(id)) stack = new ItemStack(Registries.ITEM.get(id));
            }
            var item = new ItemWidget(stack);
            grid.set(i / 3, i % 3, new ItemSlotWidget(item));
        }
        
        // → arrow
        var arrow = new TextureWidget(Identifier.of(Oracle.MOD_ID, "textures/arrow_empty.png"), 29, 16);
        
        // result slot
        var resultIdObj = Identifier.of(resultId);
        var resultStack = Registries.ITEM.containsId(resultIdObj)
                            ? new ItemStack(Registries.ITEM.get(resultIdObj), resultCount)
                            : ItemStack.EMPTY;
        var result = new ItemWidget(resultStack);
        var resultSlot = new ItemSlotWidget(result);
        
        var panel = FlowWidget.horizontal().gap(8);
        panel.setSurface(WikiSurface.BEDROCK_PANEL);
        panel.setPadding(Insets.of(8));
        panel.verticalAlignment(FlowWidget.VerticalAlignment.CENTER);
        panel.child(grid);
        panel.child(arrow);
        panel.child(resultSlot);
        return panel;
    }
    
    public static UIComponent buildImage(String location, String widthSource, String wikiId, boolean isModAsset, int contentWidthPx) {
        float widthRatio = convertImageWidth(widthSource);
        if (widthRatio <= 0) widthRatio = 0.5f;
        if (location.startsWith("@")) location = location.substring(1);
        
        // available pixel budget after scrollbar gutter + a tiny breathing margin
        int budget = Math.max(16, contentWidthPx - 12);
        
        // case 1: ingame item → render as ItemWidget
        var itemIdCandidate = Identifier.of(location);
        if (Registries.ITEM.containsId(itemIdCandidate)) {
            // items default to ~10% of content width when no width is specified
            if (widthRatio == 0.5f) widthRatio = 0.1f;
            int displaySize = Math.max(16, (int) (budget * widthRatio));
            var itemWidget = new ItemWidget(new ItemStack(Registries.ITEM.get(itemIdCandidate)));
            itemWidget.size(displaySize, displaySize);
            return itemWidget;
        }
        
        // case 2: texture path
        Identifier searchPath;
        if (isModAsset) {
            var parts = location.split(":", 2);
            var imageModId = parts.length > 0 ? parts[0] : wikiId;
            var imagePath = parts.length > 1 ? parts[1] : location;
            searchPath = Identifier.of(Oracle.MOD_ID, ROOT_DIR + "/" + wikiId + "/.assets/" + imageModId + "/" + imagePath + ".png");
        } else {
            searchPath = Identifier.of(Oracle.MOD_ID, ROOT_DIR + "/" + wikiId + "/.assets/" + wikiId + "/" + location + ".png");
        }
        
        var rm = MinecraftClient.getInstance().getResourceManager();
        var resource = rm.getResource(searchPath);
        if (resource.isEmpty()) {
            return new LabelWidget(Text.literal("Image not found: " + searchPath).formatted(Formatting.RED));
        }
        try {
            var image = NativeImage.read(resource.get().getInputStream());
            int srcW = image.getWidth();
            int srcH = image.getHeight();
            int displayW = Math.max(16, (int) (budget * widthRatio));
            int displayH = (int) (displayW * (srcH / (float) srcW));
            var widget = new TextureWidget(searchPath, srcW, srcH).region(0, 0, srcW, srcH);
            widget.size(displayW, displayH);
            return widget;
        } catch (IOException e) {
            return new LabelWidget(Text.literal("Error reading image: " + location).formatted(Formatting.RED));
        }
    }
    
    public static Map<String, String> parseFrontmatter(String markdown) {
        var document = PARSER.parse(markdown);
        var yamlVisitor = new YamlFrontMatterVisitor();
        document.accept(yamlVisitor);
        var frontmatter = yamlVisitor.getData();
        try {
            var simple = new HashMap<String, String>();
            for (var pair : frontmatter.entrySet()) {
                if (pair.getValue().isEmpty()) continue;
                simple.put(pair.getKey(), pair.getValue().getFirst().trim());
            }
            return simple;
        } catch (RuntimeException ex) {
            Oracle.LOGGER.warn("Error parsing markdown frontmatter: {} in {}", ex, markdown);
            return new HashMap<>();
        }
    }
    
    public static float convertImageWidth(String input) {
        if (input == null || input.isEmpty()) return 0.0f;
        var trimmed = input.trim();
        if (trimmed.endsWith("%")) {
            try { return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1)) / 100.0f; }
            catch (NumberFormatException e) { return 0.0f; }
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try { return Integer.parseInt(trimmed.substring(1, trimmed.length() - 1)) / 1000.0f; }
            catch (NumberFormatException e) { return 0.0f; }
        }
        if (StringUtils.isNumeric(trimmed)) {
            try { return Integer.parseInt(trimmed) / 1000.0f; }
            catch (NumberFormatException e) { return 0.0f; }
        }
        return 0.0f;
    }
}
