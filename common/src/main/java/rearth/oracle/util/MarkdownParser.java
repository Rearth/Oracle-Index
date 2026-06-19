package rearth.oracle.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.Item;
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

        var gameId = frontMatter.getOne("id");
        if (gameId != null) {
            var id = Identifier.of(gameId);
            if (Registries.ITEM.containsId(id) || Registries.BLOCK.containsId(id))
                widgets.add(buildPropertiesPanel(ContentProperties.getProperties(gameId), contentWidthPx));
        }

        return widgets;
    }

    public static String parseHeadingTitle(String markdown) {
        var document = PARSER.parse(markdown);
        var visitor = new WikiTitleVisitor();
        document.accept(visitor);

        return visitor.getTitle();
    }

    // ---------------------------------------------------------------- visitor

    private static class WikiTitleVisitor extends AbstractVisitor {
        protected String title;
        protected MutableText buffer = Text.empty();

        public String getTitle() {
            return title;
        }

        @Override
        public void visit(Heading heading) {
            buffer = Text.empty();
            visitChildren(heading);

            if (heading.getLevel() == 1 && title == null) {
                title = buffer.getString();
            }

            buffer = Text.empty();
        }

        @Override
        public void visit(org.commonmark.node.Text text) {
            if (buffer != null) {
                buffer.append(Text.literal(text.getLiteral()));
            }
        }
    }

    private static class WikiMarkdownVisitor extends WikiTitleVisitor {

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

        List<UIComponent> results() {
            return components;
        }

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

            if (heading.getLevel() == 1 && title == null) {
                title = buffer.getString();
            } else {
                var label = new LabelWidget(buffer).linkHandler(linkHandler).fillWidth();
                label.scale(Math.max(1.0f, 2.0f - heading.getLevel() * 0.2f));
                label.setPadding(Insets.of(10, 5, 0, 0));
                components.add(label);
            }

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

        @Override
        public void visit(BulletList l) {
            visitChildren(l);
        }

        @Override
        public void visit(OrderedList l) {
            visitChildren(l);
        }

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
                components.add(buildImage(image.location, image.width, this.wikiId, contentWidthPx));
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
            components.add(buildImage(image.getDestination(), "60%", wikiId, contentWidthPx));
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
                buffer.append(linkTitle.setStyle(currentStyle));
            }
            visitChildren(link);
            currentStyle = old;
        }

        @Override
        public void visit(Code inlineCode) {
            if (buffer != null) {
                buffer.append(Text.literal(inlineCode.getLiteral()).formatted(Formatting.DARK_AQUA));
            }
        }

        @Override
        public void visit(SoftLineBreak n) {
            if (buffer != null) buffer.append(Text.literal(" "));
        }

        @Override
        public void visit(HardLineBreak n) {
            if (buffer != null) buffer.append(Text.literal("\n"));
        }
    }

    // ---------------------------------------------------------------- helpers

    public static MutableText getLinkText(String link, String activeWikiId, Identifier sourceEntryPath) {
        if (link.startsWith("@")) {
            Identifier id = Identifier.tryParse(link.substring(1));
            if (id != null && id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
                Item item = Registries.ITEM.get(id);
                if (item != null) {
                    return Text.translatable(item.getTranslationKey());
                }
            }
        }

        return Text.literal(getLinkTextLiteral(link, activeWikiId, sourceEntryPath));
    }

    public static String getLinkTextLiteral(String link, String activeWikiId, Identifier sourceEntryPath) {
        var linkTarget = getLinkTarget(link, activeWikiId, sourceEntryPath);
        if (linkTarget == null) return "<invalid link>";
        var rm = MinecraftClient.getInstance().getResourceManager();
        var rc = rm.getResource(linkTarget);
        if (rc.isEmpty()) return "<invalid link>";
        String title = TitleLookup.getTitle(linkTarget);
        return title != null ? title : "<invalid link>";
    }

    @Nullable
    public static Identifier getLinkTarget(String link, String activeWikiId, Identifier sourceEntryPath) {
        Identifier targetFile;
        if (link.startsWith("@") || link.contains(":")) {
            var id = link.startsWith("@") ? link.substring(1) : link;
            targetFile = OracleClient.CONTENT_ID_MAP.get(id);
        } else if (link.startsWith("$")) {
            var format = OracleClient.getWikiFormat(activeWikiId);
            var p = "books/" + activeWikiId + "/" + format.getDocsPagePath(link.substring(1));
            if (!p.endsWith(".mdx")) p += ".mdx";
            targetFile = Identifier.of(Oracle.MOD_ID, p);
        } else if (link.startsWith("+")) {
            var id = link.substring(1);
            targetFile = OracleClient.getPage(activeWikiId, id);
        } else {
            var p = OracleScreen.parsePathLink(link, sourceEntryPath);
            if (!p.endsWith(".mdx")) p += ".mdx";
            targetFile = Identifier.of(Oracle.MOD_ID, p);
        }
        return targetFile;
    }

    private static UIComponent buildTitlePanel(Predicate<String> linkHandler, Frontmatter frontMatter, Identifier pageId, int contentWidthPx) {
        var iconId = frontMatter.getOrDefault("icon", "");
        if (iconId.isBlank()) iconId = frontMatter.getOrDefault("id", "");
        ItemStack iconStack = getIconStack(iconId);

        List<ItemStack> itemStacks = new ArrayList<>();
        List<String> ids = frontMatter.getAll("id");
        if (ids != null && ids.size() > 1) {
            for (String id : ids) {
                ItemStack stack = getIconStack(id);
                if (!stack.isEmpty()) {
                    itemStacks.add(stack);
                }
            }
        }

        return new PageTitleWidget(
            Text.literal(TitleLookup.getTitle(pageId)).formatted(Formatting.DARK_GRAY),
            iconStack,
            itemStacks,
            linkHandler,
            contentWidthPx
        );
    }

    private static ItemStack getIconStack(String iconId) {
        if (Identifier.validate(iconId).isSuccess() && Registries.ITEM.containsId(Identifier.of(iconId))) {
            return new ItemStack(Registries.ITEM.get(Identifier.of(iconId)));
        }
        return ItemStack.EMPTY;
    }

    private static class PageTitleWidget extends UIComponent {
        private static final int ICON_PANEL_SIZE = 58;
        private static final int ICON_ITEM_SIZE = 50;

        private static final int ITEM_PANEL_SIZE = 32;
        private static final int ITEM_ICON_SIZE = 24;
        private static final int ITEM_PADDING = (ITEM_PANEL_SIZE - ITEM_ICON_SIZE) / 2;
        private static final int ITEMS_MARGIN = 2;

        private static final int TITLE_OVERLAP = 12;
        private static final int TITLE_PAD_X = 14;
        private static final int TITLE_PAD_Y = 9;

        private final LabelWidget titleLabel;
        private final ItemWidget icon;
        private final List<ItemWidget> items;
        private final int contentWidthPx;

        private int titleX;
        private int titleY;
        private int titleW;
        private int titleH;
        private int iconX;
        private int iconY;

        PageTitleWidget(Text title, ItemStack iconStack, List<ItemStack> itemStacks, Predicate<String> linkHandler, int contentWidthPx) {
            this.titleLabel = new LabelWidget(title).scale(2f).linkHandler(linkHandler);
            this.icon = iconStack.isEmpty() ? null : new ItemWidget(iconStack);
            if (icon != null) {
                icon.setTooltipMode(TooltipMode.HIDDEN);
                icon.setHideItemDecorations(true);
            }
            this.items = itemStacks.stream()
                .map(ItemWidget::new)
                .peek(w -> {
                    w.setTooltipMode(TooltipMode.NAME_ONLY);
                    w.setHideItemDecorations(true);
                    w.size(ITEM_ICON_SIZE, ITEM_ICON_SIZE);
                })
                .toList();
            this.contentWidthPx = contentWidthPx;
        }

        @Override
        public int getPreferredWidth(int widthHint) {
            int maxWidth = widthHint > 0 ? widthHint : contentWidthPx;
            int labelMaxWidth = labelMaxWidth(maxWidth);
            titleLabel.wrapWidth(labelMaxWidth);
            int titlePanelWidth = titleLabel.getPreferredWidth(labelMaxWidth) + TITLE_PAD_X * 2;
            int itemsRowWidth = Math.min(maxWidth, items.size() * ITEM_PANEL_SIZE);
            return Math.max(leadingWidth() + titlePanelWidth, itemsRowWidth);
        }

        @Override
        public int getPreferredHeight(int widthHint) {
            int maxWidth = widthHint > 0 ? widthHint : contentWidthPx;
            int labelMaxWidth = labelMaxWidth(maxWidth);
            titleLabel.wrapWidth(labelMaxWidth);
            int titlePanelHeight = titleLabel.getPreferredHeight(labelMaxWidth) + TITLE_PAD_Y * 2;
            int itemRowsHeight = getOuterRowsHeight(maxWidth);
            return Math.max(icon == null ? 0 : ICON_PANEL_SIZE, titlePanelHeight) + itemRowsHeight;
        }

        private int getMaxCols(int maxWidth) {
            return maxWidth / ITEM_PANEL_SIZE;
        }

        private int getInnerRowsHeight(int maxWidth) {
            int itemCols = getMaxCols(maxWidth);
            return (int) Math.ceil(items.size() / (double) itemCols) * ITEM_PANEL_SIZE;
        }

        private int getOuterRowsHeight(int maxWidth) {
            int height = getInnerRowsHeight(maxWidth);
            return height > 0 ? height + ITEMS_MARGIN : 0;
        }

        @Override
        public void layout(int parentWidthHint, int parentHeightHint) {
            int centerOffset = getOuterRowsHeight(width) / 2;

            int labelMaxWidth = Math.max(80, width - leadingWidth() - TITLE_PAD_X * 2);
            titleLabel.wrapWidth(labelMaxWidth);
            int labelW = titleLabel.getPreferredWidth(labelMaxWidth);
            int labelH = titleLabel.getPreferredHeight(labelMaxWidth);
            titleW = labelW + TITLE_PAD_X * 2;
            titleH = labelH + TITLE_PAD_Y * 2;
            titleX = x + leadingWidth();
            titleY = y + (height - titleH) / 2 - centerOffset;
            int offset = icon != null ? TITLE_PAD_X / 2 : 0;
            titleLabel.setPosition(titleX + TITLE_PAD_X + offset, titleY + TITLE_PAD_Y);
            titleLabel.setLayoutSize(labelW, labelH);
            titleLabel.layout(labelW, labelH);

            if (icon != null) {
                iconX = x;
                iconY = y + (height - ICON_PANEL_SIZE) / 2 - centerOffset;
                icon.setPosition(iconX + (ICON_PANEL_SIZE - ICON_ITEM_SIZE) / 2, iconY + (ICON_PANEL_SIZE - ICON_ITEM_SIZE) / 2);
                icon.setLayoutSize(ICON_ITEM_SIZE, ICON_ITEM_SIZE);
                icon.layout(ICON_ITEM_SIZE, ICON_ITEM_SIZE);
            }

            if (!items.isEmpty()) {
                int cols = getMaxCols(width);
                int rowsHeight = getInnerRowsHeight(width);
                int baseX = x;
                int baseY = y + height - rowsHeight;

                for (int i = 0; i < items.size(); i++) {
                    ItemWidget item = items.get(i);
                    int row = i / cols;
                    int col = i % cols;
                    int iconX = baseX + ITEM_PADDING + col * ITEM_PANEL_SIZE;
                    int iconY = baseY + ITEM_PADDING + row * ITEM_PANEL_SIZE;

                    item.setPosition(iconX, iconY);
                    item.setLayoutSize(ITEM_ICON_SIZE, ITEM_ICON_SIZE);
                    item.layout(ITEM_ICON_SIZE, ITEM_ICON_SIZE);
                }
            }
        }

        @Override
        protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
            WikiSurface.BEDROCK_PANEL.render(context, titleX, titleY, titleW, titleH);
            titleLabel.render(context, mouseX, mouseY, delta);

            if (icon != null) {
                WikiSurface.BEDROCK_PANEL.render(context, iconX, iconY, ICON_PANEL_SIZE, ICON_PANEL_SIZE);
                icon.render(context, mouseX, mouseY, delta);
            }

            if (!items.isEmpty()) {
                for (ItemWidget item : items) {
                    WikiSurface.BEDROCK_PANEL.render(context, item.getX() - ITEM_PADDING, item.getY() - ITEM_PADDING, ITEM_PANEL_SIZE, ITEM_PANEL_SIZE);
                    item.render(context, mouseX, mouseY, delta);
                }
            }
        }

        @Override
        public List<Text> tooltip(int mouseX, int mouseY) {
            if (icon != null && icon.isInBounds(mouseX, mouseY)) {
                return icon.tooltip(mouseX, mouseY);
            }
            for (ItemWidget item : items) {
                if (item.isInBounds(mouseX, mouseY)) {
                    return item.tooltip(mouseX, mouseY);
                }
            }
            return super.tooltip(mouseX, mouseY);
        }

        private int leadingWidth() {
            return icon == null ? 0 : ICON_PANEL_SIZE - TITLE_OVERLAP;
        }

        private int labelMaxWidth(int maxWidth) {
            return Math.max(80, maxWidth - leadingWidth() - TITLE_PAD_X * 2);
        }
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
        int innerWidth = Math.clamp(Math.max(titleWidth, keyWidth + valueWidth + 28) + 20, 160, Math.max(contentWidthPx, 165));
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

    public static UIComponent buildImage(String location, String widthSource, String wikiId, int contentWidthPx) {
        var widthRatio = convertImageWidth(widthSource);
        if (widthRatio <= 0) widthRatio = 0.5f;
        if (location.startsWith("@")) location = location.substring(1);

        // available pixel budget after scrollbar gutter + a tiny breathing margin
        var budget = Math.max(16, contentWidthPx - 12);

        // case 1: ingame item → render as ItemWidget
        var itemIdCandidate = Identifier.of(location);
        if (Registries.ITEM.containsId(itemIdCandidate)) {
            // items default to ~10% of content width when no width is specified
            if (widthRatio == 0.5f) widthRatio = 0.1f;
            int displaySize = Math.max(16, (int) (budget * widthRatio));
            var itemWidget = new ItemWidget(new ItemStack(Registries.ITEM.get(itemIdCandidate)));
            itemWidget.size(displaySize, displaySize);
            itemWidget.setHideItemDecorations(true);
            return itemWidget;
        }

        // case 2: texture path
        var assetsRoot = OracleClient.getWikiFormat(wikiId).getAssetsRoot();
        Identifier searchPath;
        var parts = location.split(":", 2);
        var imageModId = parts.length > 0 ? parts[0] : wikiId;
        var imagePath = parts.length > 1 ? parts[1] : location;
        var extension = imagePath.contains(".") ? "" : ".png";
        searchPath = Identifier.of(Oracle.MOD_ID, ROOT_DIR + "/" + wikiId + assetsRoot + "/" + imageModId + "/" + imagePath + extension);

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
            var widget = new TextureWidget(searchPath, srcW, srcH) {
                @Override
                public @Nullable FlowWidget.HorizontalAlignment getOverrideAlignment() {
                    return FlowWidget.HorizontalAlignment.CENTER;
                }
            };
            widget.region(0, 0, srcW, srcH);
            widget.size(displayW, displayH);
            return widget;
        } catch (IOException e) {
            return new LabelWidget(Text.literal("Error reading image: " + location).formatted(Formatting.RED));
        }
    }

    public static Frontmatter parseFrontmatter(String markdown) {
        var document = PARSER.parse(markdown);
        var yamlVisitor = new YamlFrontMatterVisitor();
        document.accept(yamlVisitor);
        var frontmatter = yamlVisitor.getData();
        try {
            var inner = new HashMap<String, List<String>>();
            for (var pair : frontmatter.entrySet()) {
                if (pair.getValue().isEmpty()) continue;
                inner.put(pair.getKey(), pair.getValue().stream().map(String::trim).toList());
            }
            return new Frontmatter(inner);
        } catch (RuntimeException ex) {
            Oracle.LOGGER.warn("Error parsing markdown frontmatter: {} in {}", ex, markdown);
            return new Frontmatter(Map.of());
        }
    }

    public static float convertImageWidth(String input) {
        if (input == null || input.isEmpty()) return 0.0f;
        var trimmed = input.trim();
        if (trimmed.endsWith("%")) {
            try {
                return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1)) / 100.0f;
            } catch (NumberFormatException e) {
                return 0.0f;
            }
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                return Integer.parseInt(trimmed.substring(1, trimmed.length() - 1)) / 1000.0f;
            } catch (NumberFormatException e) {
                return 0.0f;
            }
        }
        if (StringUtils.isNumeric(trimmed)) {
            try {
                return Integer.parseInt(trimmed) / 1000.0f;
            } catch (NumberFormatException e) {
                return 0.0f;
            }
        }
        return 0.0f;
    }

    public record Frontmatter(Map<String, List<String>> map) {
        @Nullable
        public List<String> getAll(String key) {
            return this.map.get(key);
        }

        @Nullable
        public String getOne(String key) {
            List<String> values = this.map.get(key);
            if (values == null) {
                return null;
            }
            return values.size() == 1 ? values.getFirst() : null;
        }

        public String getOrDefault(String key, String _default) {
            String value = getOne(key);
            return value != null ? value : _default;
        }

        public boolean containsKey(String key) {
            return this.map.containsKey(key);
        }
    }
}
