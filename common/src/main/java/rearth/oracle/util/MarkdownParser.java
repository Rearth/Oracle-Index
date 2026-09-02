package rearth.oracle.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import rearth.oracle.Oracle;
import rearth.oracle.OracleClient;
import rearth.oracle.ui.OracleScreen;
import rearth.oracle.ui.widgets.*;
import rearth.oracle.ui.widgets.FlowWidget.HorizontalAlignment;
import rearth.oracle.ui.widgets.TableWidget.Cell;
import rearth.oracle.util.MdxAttributes.Match;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static rearth.oracle.OracleClient.ROOT_DIR;

/**
 * Markdown / MDX → wiki widget tree converter.
 * Replaces the previous owo-lib-based parser.
 */
public class MarkdownParser {

    private static final Identifier WIKI_LINK_EVENT =
        Identifier.fromNamespaceAndPath(Oracle.MOD_ID, "wiki_link");
    private static final String[] removedLines = {"<center>", "</center>", "<div>", "</div>", "<span>", "</span>"};

    private static final List<Extension> EXTENSIONS = List.of(YamlFrontMatterExtension.create(), TablesExtension.create());
    private static final Set<Class<? extends Block>> ENABLED_BLOCKS = Set.of(
        Heading.class, HtmlBlock.class, ThematicBreak.class,
        FencedCodeBlock.class, BlockQuote.class, ListBlock.class
    );

    private static final Parser PARSER = Parser.builder()
        .enabledBlockTypes(ENABLED_BLOCKS)
        .extensions(EXTENSIONS)
        .customBlockParserFactory(new MdxBlockFactory())
        .customInlineContentParserFactory(new HoverText.ParserFactory())
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
            var id = Identifier.parse(gameId);
            if (BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.BLOCK.containsKey(id))
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
        protected MutableComponent buffer = Component.empty();

        public String getTitle() {
            return title;
        }

        @Override
        public void visit(Heading heading) {
            buffer = Component.empty();
            visitChildren(heading);

            if (heading.getLevel() == 1 && title == null) {
                title = buffer.getString();
            }

            buffer = Component.empty();
        }

        @Override
        public void visit(org.commonmark.node.Text text) {
            if (buffer != null) {
                buffer.append(Component.literal(text.getLiteral()));
            }
        }
    }

    private static class WikiMarkdownVisitor extends WikiTitleVisitor {

        private final Predicate<String> linkHandler;
        private final String wikiId;
        private final Identifier contentPath;
        private final int contentWidthPx;

        private List<UIComponent> components = new ArrayList<>();
        private MutableComponent buffer = Component.empty();
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
            buffer = Component.empty();
            currentIndentation = 0;
        }

        private List<UIComponent> collectChildren(Node node) {
            var previousComponents = this.components;
            var previousBuffer = this.buffer;
            var collected = new ArrayList<UIComponent>();
            this.components = collected;
            this.buffer = Component.empty();
            visitChildren(node);
            flushBuffer();
            this.components = previousComponents;
            this.buffer = previousBuffer;
            return collected;
        }

        private MutableComponent collectInline(Node node, Style baseStyle) {
            var previousBuffer = this.buffer;
            var previousStyle = this.currentStyle;
            this.buffer = Component.empty();
            this.currentStyle = baseStyle;
            visitChildren(node);
            var collected = this.buffer;
            this.buffer = previousBuffer;
            this.currentStyle = previousStyle;
            return collected;
        }

        @Override
        public void visit(Paragraph paragraph) {
            visitChildren(paragraph);
            flushBuffer();
        }

        @Override
        public void visit(Heading heading) {
            buffer = Component.empty();
            stripHeadingAttributes(heading);
            var oldStyle = currentStyle;
            currentStyle = currentStyle.withColor(ChatFormatting.GRAY);
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

            buffer = Component.empty();
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            flushBuffer();
            var panel = FlowWidget.vertical();
            panel.setSurface(WikiSurface.BEDROCK_PANEL_DARK);
            panel.setPadding(Insets.of(6));
            var text = Component.literal(codeBlock.getLiteral()).withStyle(ChatFormatting.GRAY);
            panel.child(new LabelWidget(text));
            components.add(panel);
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            flushBuffer();
            var alert = GitHubAlert.consume(blockQuote);
            var body = collectChildren(blockQuote);

            if (alert != null) {
                var callout = new CalloutWidget(alert.variant(), alert.title(), alert.collapsible(), alert.collapsed());
                for (var child : body) callout.addBodyChild(child);
                components.add(callout);
            }
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
                buffer.append(Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY));
            } else if (parent instanceof OrderedList orderedList) {
                int index = 1;
                var sibling = listItem.getPrevious();
                while (sibling != null) {
                    if (sibling instanceof ListItem) index++;
                    sibling = sibling.getPrevious();
                }
                int start = Objects.requireNonNullElse(orderedList.getMarkerStartNumber(), 1);
                int n = start + index - 1;
                buffer.append(Component.literal(n + ". ").withStyle(ChatFormatting.DARK_GRAY));
            }
            visitChildren(listItem);
            flushBuffer();
            this.currentIndentation = 0;
        }

        @Override
        public void visit(CustomBlock customBlock) {
            switch (customBlock) {
                case MdxComponentBlock.CraftingRecipeBlock recipe -> {
                    flushBuffer();
                    components.add(buildRecipe(recipe.slots, recipe.result, recipe.count));
                }
                case MdxComponentBlock.AssetBlock asset -> {
                    flushBuffer();
                    components.add(buildImage(asset.location, ImageStyle.ofWidthSource(asset.width), wikiId, contentWidthPx));
                }
                case MdxComponentBlock.CalloutBlock callout -> {
                    flushBuffer();
                    var inner = collectChildren(callout);
                    var title = callout.title == null ? null : Component.literal(callout.title);
                    var widget = new CalloutWidget(callout.variant, title, callout.collapsible, callout.collapsed);
                    for (var c : inner) widget.addBodyChild(c);
                    components.add(widget);
                }
                case TableBlock table -> buildTable(table);
                default -> visitChildren(customBlock);
            }
        }

        @Override
        public void visit(CustomNode customNode) {
            if (customNode instanceof HoverText hoverText) {
                var style = currentStyle
                    .withUnderlined(true)
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText.getHint())));
                buffer.append(Component.literal(hoverText.getLabel()).setStyle(style));
                return;
            }
            super.visit(customNode);
        }

        @Override
        public void visit(HtmlBlock htmlBlock) {
            visitRawHtml(htmlBlock.getLiteral());
        }

        @Override
        public void visit(HtmlInline htmlInline) {
            visitRawHtml(htmlInline.getLiteral());
        }

        private void visitRawHtml(String html) {
            org.jsoup.nodes.Document fragment = Jsoup.parseBodyFragment(html);

            Element image = fragment.selectFirst("img");
            if (image != null && image.hasAttr("src")) {
                flushBuffer();
                components.add(buildImage(image.attr("src"), ImageStyle.ofHtml(image), wikiId, contentWidthPx));
            }
        }

        @Override
        public void visit(Image image) {
            ImageStyle style = ImageStyle.consume(image);
            UIComponent widget = buildImage(image.getDestination(), style, wikiId, contentWidthPx);
            flushBuffer();

            String caption = altText(image);
            if (isStandalone(image) && !caption.isBlank()) {
                components.add(new FigureWidget(widget, Component.literal(caption), style.alignment()));
            } else {
                components.add(widget);
            }
        }

        private void buildTable(TableBlock table) {
            flushBuffer();
            ArrayList<List<Cell>> rows = new ArrayList<>();
            boolean hasHeader = false;

            for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
                boolean header = section instanceof TableHead;

                for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                    if (!(row instanceof TableRow)) continue;

                    ArrayList<Cell> cells = new ArrayList<>();
                    for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                        if (!(cell instanceof TableCell tableCell)) continue;

                        Style style = header ? Style.EMPTY.withBold(true) : Style.EMPTY;
                        cells.add(new TableWidget.Cell(collectInline(tableCell, style), getCellAlignment(tableCell)));
                    }

                    if (header) hasHeader = true;
                    rows.add(cells);
                }
            }

            if (!rows.isEmpty()) {
                components.add(new TableWidget(rows, hasHeader, linkHandler));
            }
        }

        @Override
        public void visit(org.commonmark.node.Text text) {
            if (buffer != null) buffer.append(Component.literal(text.getLiteral()).setStyle(currentStyle));
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
            var clickEvent = new ClickEvent.Custom(
                WIKI_LINK_EVENT,
                Optional.of(StringTag.valueOf(link.getDestination()))
            );
            currentStyle = currentStyle.withColor(ChatFormatting.BLUE).withUnderlined(true).withClickEvent(clickEvent);

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
                buffer.append(Component.literal(inlineCode.getLiteral()).withStyle(ChatFormatting.DARK_AQUA));
            }
        }

        @Override
        public void visit(SoftLineBreak n) {
            if (buffer != null) buffer.append(Component.literal(" "));
        }

        @Override
        public void visit(HardLineBreak n) {
            if (buffer != null) buffer.append(Component.literal("\n"));
        }
    }

    private static void stripHeadingAttributes(Heading heading) {
        Node last = heading.getLastChild();
        if (!(last instanceof org.commonmark.node.Text text)) return;

        Match match = MdxAttributes.matchTrailing(text.getLiteral());
        if (match == null) return;

        text.setLiteral(match.remainder());
    }

    private static boolean isStandalone(Image image) {
        if (!(image.getParent() instanceof Paragraph paragraph)) return false;

        for (var sibling = paragraph.getFirstChild(); sibling != null; sibling = sibling.getNext()) {
            if (sibling == image || sibling instanceof Text text && text.getLiteral().isBlank() || sibling instanceof SoftLineBreak)
                continue;
            return false;
        }

        return true;
    }

    private static String altText(Image image) {
        StringBuilder alt = new StringBuilder();
        for (var child = image.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof org.commonmark.node.Text text) {
                alt.append(text.getLiteral());
            }
        }
        return alt.toString().trim();
    }

    public record GitHubAlert(CalloutVariant variant, @Nullable Component title, boolean collapsible, boolean collapsed) {
        private static final Pattern HEADER = Pattern.compile("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]([+-]?)\\s*(.*)$");

        @Nullable
        public static GitHubAlert consume(BlockQuote blockQuote) {
            if (!(blockQuote.getFirstChild() instanceof Paragraph paragraph)) return null;

            // collect the first line's plain text; formatting inside the header is not supported
            var line = new StringBuilder();
            var consumed = new ArrayList<Node>();
            Node lineBreak = null;
            for (var child = paragraph.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof org.commonmark.node.Text text) {
                    line.append(text.getLiteral());
                    consumed.add(child);
                } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                    lineBreak = child;
                    break;
                } else {
                    break;
                }
            }

            var matcher = HEADER.matcher(line.toString().trim());
            if (!matcher.matches()) return null;

            for (var node : consumed) node.unlink();
            if (lineBreak != null) lineBreak.unlink();
            if (paragraph.getFirstChild() == null) paragraph.unlink();

            var variant = CalloutVariant.byName(matcher.group(1), CalloutVariant.NOTE);
            var marker = matcher.group(2);
            var title = matcher.group(3).isBlank() ? null : Component.literal(matcher.group(3).trim());
            boolean collapsed = "-".equals(marker);
            boolean collapsible = collapsed || "+".equals(marker);
            return new GitHubAlert(variant, title, collapsible, collapsed);
        }
    }

    public record ImageStyle(@Nullable Float widthRatio, @Nullable Integer width, @Nullable Integer height,
                             boolean item, FlowWidget.HorizontalAlignment alignment
    ) {
        private static final int ITEM_SIZE = 32;

        public static final ImageStyle DEFAULT = new ImageStyle(null, null, null, false, FlowWidget.HorizontalAlignment.CENTER);

        private static ImageStyle consume(Image image) {
            if (!(image.getNext() instanceof org.commonmark.node.Text text)) return DEFAULT;

            Match match = MdxAttributes.matchLeading(text.getLiteral());
            if (match == null) return DEFAULT;

            text.setLiteral(match.remainder());

            return of(match.attributes());
        }

        private static ImageStyle of(MdxAttributes attributes) {
            HorizontalAlignment alignment = attributes.has("right")
                ? FlowWidget.HorizontalAlignment.RIGHT
                : attributes.has("left") ? FlowWidget.HorizontalAlignment.LEFT
                : FlowWidget.HorizontalAlignment.CENTER;
            String rawWidth = attributes.get("width");
            Float ratio = rawWidth != null && rawWidth.endsWith("%") ? convertImageWidth(rawWidth) : null;
            return new ImageStyle(ratio, attributes.getPixels("width"), attributes.getPixels("height"), attributes.has("item"), alignment);
        }

        private static ImageStyle ofHtml(Element element) {
            Map<String, String> attributes = new HashMap<>();
            if (element.hasAttr("width")) {
                attributes.put("width", element.attr("width"));
            }
            if (element.hasAttr("height")) {
                attributes.put("height", element.attr("height"));
            }

            Set<String> flags = new HashSet<>();
            for (var name : element.attr("class").split("\\s+")) {
                if (!name.isBlank()) flags.add(name);
            }

            if (element.hasAttr("align")) {
                flags.add(element.attr("align").toLowerCase(Locale.ROOT));
            }

            return of(new MdxAttributes(attributes, flags));
        }

        private static ImageStyle ofWidthSource(@Nullable String widthSource) {
            float ratio = convertImageWidth(widthSource);
            return new ImageStyle(ratio > 0 ? ratio : null, null, null, false, FlowWidget.HorizontalAlignment.CENTER);
        }

        private int resolveWidth(int budget, float defaultRatio) {
            if (item) return ITEM_SIZE;
            if (width != null && width > 0) return Math.min(width, budget);
            float ratio = widthRatio != null && widthRatio > 0 ? widthRatio : defaultRatio;
            return Math.max(16, (int) (budget * ratio));
        }
    }

    // ---------------------------------------------------------------- helpers

    public static MutableComponent getLinkText(String link, String activeWikiId, Identifier sourceEntryPath) {
        int anchor = link.indexOf('#');
        if (anchor > 0) link = link.substring(0, anchor);

        if (link.startsWith("@")) {
            Identifier id = Identifier.tryParse(link.substring(1));
            if (id != null && id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item != null) {
                    return Component.translatable(item.getDescriptionId());
                }
            }
        }

        return Component.literal(getLinkTextLiteral(link, activeWikiId, sourceEntryPath));
    }

    public static String getLinkTextLiteral(String link, String activeWikiId, Identifier sourceEntryPath) {
        var linkTarget = getLinkTarget(link, activeWikiId, sourceEntryPath);
        if (linkTarget == null) return "<invalid link>";
        var rm = Minecraft.getInstance().getResourceManager();
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
            targetFile = Identifier.fromNamespaceAndPath(Oracle.MOD_ID, p);
        } else if (link.startsWith("+")) {
            var id = link.substring(1);
            targetFile = OracleClient.getPage(activeWikiId, id);
        } else {
            var p = OracleScreen.parsePathLink(link, sourceEntryPath);
            if (!p.endsWith(".mdx")) p += ".mdx";
            targetFile = Identifier.fromNamespaceAndPath(Oracle.MOD_ID, p);
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
            Component.literal(TitleLookup.getTitle(pageId)).withStyle(ChatFormatting.DARK_GRAY),
            iconStack,
            itemStacks,
            linkHandler,
            contentWidthPx
        );
    }

    private static FlowWidget.HorizontalAlignment getCellAlignment(TableCell cell) {
        var alignment = cell.getAlignment();
        if (alignment == null) {
            return FlowWidget.HorizontalAlignment.LEFT;
        }
        return switch (alignment) {
            case LEFT -> FlowWidget.HorizontalAlignment.LEFT;
            case CENTER -> FlowWidget.HorizontalAlignment.CENTER;
            case RIGHT -> FlowWidget.HorizontalAlignment.RIGHT;
        };
    }

    private static ItemStack getIconStack(String iconId) {
        if (Identifier.tryParse(iconId) != null && BuiltInRegistries.ITEM.containsKey(Identifier.parse(iconId))) {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(iconId)));
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

        PageTitleWidget(Component title, ItemStack iconStack, List<ItemStack> itemStacks, Predicate<String> linkHandler, int contentWidthPx) {
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
        protected void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
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
        public List<Component> tooltip(int mouseX, int mouseY) {
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

    private static UIComponent buildPropertiesPanel(Map<String, Component> properties, int contentWidthPx) {
        var tr = Minecraft.getInstance().font;
        int titleWidth = tr.width("Details");
        int keyWidth = 0;
        int valueWidth = 0;
        for (var entry : properties.entrySet()) {
            keyWidth = Math.max(keyWidth, tr.width(entry.getKey()));
            valueWidth = Math.max(valueWidth, tr.width(entry.getValue()));
        }
        int innerWidth = Math.clamp(Math.max(titleWidth, keyWidth + valueWidth + 28) + 20, 160, Math.max(contentWidthPx, 165));
        var outer = FlowWidget.vertical().gap(2);
        outer.setSurface(WikiSurface.BEDROCK_PANEL_DARK);
        outer.setPadding(Insets.of(10));
        outer.size(innerWidth, 0);
        outer.horizontalAlignment(FlowWidget.HorizontalAlignment.CENTER);
        outer.child(new LabelWidget(Component.literal("Details").withStyle(ChatFormatting.BOLD, ChatFormatting.GRAY)));

        for (var entry : properties.entrySet()) {
            outer.child(new PropertyRowWidget(Component.literal(entry.getKey()).withStyle(ChatFormatting.GOLD), entry.getValue()));
        }
        return outer;
    }

    private static class PropertyRowWidget extends UIComponent {
        private final Component key;
        private final Component value;

        PropertyRowWidget(Component key, Component value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public int getPreferredWidth(int widthHint) {
            return widthHint > 0 ? widthHint : Minecraft.getInstance().font.width(key) + 28 + Minecraft.getInstance().font.width(value);
        }

        @Override
        public int getPreferredHeight(int widthHint) {
            return Minecraft.getInstance().font.lineHeight;
        }

        @Override
        protected void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            var tr = Minecraft.getInstance().font;
            context.text(tr, key, x, y, 0xFFFFFFFF, false);
            context.text(tr, value, x + width - tr.width(value), y, 0xFFFFFFFF, false);
        }
    }

    public static UIComponent buildRecipe(List<String> inputs, String resultId, int resultCount) {
        if (inputs.size() != 9) {
            return new LabelWidget(Component.literal("Invalid crafting recipe data: expected 9 inputs").withStyle(ChatFormatting.RED));
        }

        // Layered: a 3x3 grid of slots with items overlaid on top.
        var grid = new GridWidget(3, 3, ItemSlotWidget.SLOT_SIZE, ItemSlotWidget.SLOT_SIZE).gap(0, 0);
        grid.setPadding(Insets.of(3));
        for (int i = 0; i < 9; i++) {
            var input = inputs.get(i);
            ItemStack stack = ItemStack.EMPTY;
            if (!input.isEmpty() && !input.equals("minecraft:air")) {
                var id = Identifier.parse(input);
                if (BuiltInRegistries.ITEM.containsKey(id)) stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            }
            var item = new ItemWidget(stack);
            grid.set(i / 3, i % 3, new ItemSlotWidget(item));
        }

        // → arrow
        var arrow = new TextureWidget(Identifier.fromNamespaceAndPath(Oracle.MOD_ID, "textures/arrow_empty.png"), 29, 16);

        // result slot
        var resultIdObj = Identifier.parse(resultId);
        var resultStack = BuiltInRegistries.ITEM.containsKey(resultIdObj)
            ? new ItemStack(BuiltInRegistries.ITEM.getValue(resultIdObj), resultCount)
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

    public static Identifier resolveAssetPath(String location, String wikiId, String defaultExtension) {
        if (location.startsWith("@")) location = location.substring(1);

        var assetsRoot = OracleClient.getWikiFormat(wikiId).getAssetsRoot();
        var parts = location.split(":", 2);
        var assetModId = parts.length > 1 ? parts[0] : wikiId;
        var assetPath = parts.length > 1 ? parts[1] : location;
        var extension = assetPath.contains(".") ? "" : defaultExtension;

        return Identifier.fromNamespaceAndPath(
            Oracle.MOD_ID,
            ROOT_DIR + "/" + wikiId + assetsRoot + "/" + assetModId + "/" + assetPath + extension
        );
    }

    public static UIComponent buildImage(String location, ImageStyle style, String wikiId, int contentWidthPx) {
        if (location == null || location.isBlank()) {
            return new LabelWidget(Component.literal("Missing image location").withStyle(ChatFormatting.RED));
        }
        if (location.startsWith("@")) location = location.substring(1);

        // available pixel budget after scrollbar gutter + a tiny breathing margin
        var budget = Math.max(16, contentWidthPx - 12);

        // case 1: ingame item → render as ItemWidget
        var itemIdCandidate = Identifier.tryParse(location);
        if (itemIdCandidate != null && BuiltInRegistries.ITEM.containsKey(itemIdCandidate)) {
            // items default to ~10% of content width when no size is specified
            int displaySize = style.resolveWidth(budget, 0.1f);
            var itemWidget = new ItemWidget(new ItemStack(BuiltInRegistries.ITEM.getValue(itemIdCandidate)));
            itemWidget.size(displaySize, displaySize);
            itemWidget.setHideItemDecorations(true);
            return itemWidget;
        }

        // case 2: texture path
        var searchPath = resolveAssetPath(location, wikiId, ".png");
        var rm = Minecraft.getInstance().getResourceManager();
        var resource = rm.getResource(searchPath);
        if (resource.isEmpty()) {
            return new LabelWidget(Component.literal("Image not found: " + searchPath).withStyle(ChatFormatting.RED));
        }
        try {
            var image = NativeImage.read(resource.get().open());
            int srcW = image.getWidth();
            int srcH = image.getHeight();
            int displayW = style.resolveWidth(budget, 0.5f);
            int displayH = style.item() ? displayW
                : style.height() != null && style.height() > 0 ? style.height()
                : (int) (displayW * (srcH / (float) srcW));
            var alignment = style.alignment();
            var widget = new TextureWidget(searchPath, srcW, srcH) {
                @Override
                public FlowWidget.HorizontalAlignment getOverrideAlignment() {
                    return alignment;
                }
            };
            widget.region(0, 0, srcW, srcH);
            widget.size(displayW, displayH);
            return widget;
        } catch (IOException e) {
            return new LabelWidget(Component.literal("Error reading image: " + location).withStyle(ChatFormatting.RED));
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
