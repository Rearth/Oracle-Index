package rearth.oracle.util;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.util.NinePatchTexture;
import net.minecraft.client.MinecraftClient;
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
import rearth.oracle.ui.components.ScalableLabelComponent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

import static rearth.oracle.OracleClient.ROOT_DIR;

// a very basic and primitive (and hacky) ghetto markdown to owo lib parser
public class MarkdownParser {
    
    private static final String[] removedLines = new String[]{"<center>", "</center>", "<div>", "</div>", "<span>", "</span>"};
    
    public static final Identifier ITEM_SLOT = Identifier.of(Oracle.MOD_ID, "textures/item_cell.png");
    
    public static Surface ORACLE_PANEL = (context, component) -> NinePatchTexture.draw(Identifier.of(Oracle.MOD_ID, "bedrock_panel"), context, component);
    public static Surface ORACLE_PANEL_HOVER = (context, component) -> NinePatchTexture.draw(Identifier.of(Oracle.MOD_ID, "bedrock_panel_hover"), context, component);
    public static Surface ORACLE_PANEL_PRESSED = (context, component) -> NinePatchTexture.draw(Identifier.of(Oracle.MOD_ID, "bedrock_panel_pressed"), context, component);
    public static Surface ORACLE_PANEL_DARK = (context, component) -> NinePatchTexture.draw(Identifier.of(Oracle.MOD_ID, "bedrock_panel_dark"), context, component);
    
    private static final List<Extension> EXTENSIONS = List.of(YamlFrontMatterExtension.create());
    private static final Set<Class<? extends Block>> ENABLED_BLOCKS = Set.of(
      Heading.class,
      HtmlBlock.class,
      ThematicBreak.class,
      FencedCodeBlock.class,
      BlockQuote.class,
      ListBlock.class
      // indentedblock is missing compared to default here. This is to allow proper parsing of content inside html tags that are indented for readability
    );
    
    private static final Parser PARSER = Parser.builder()
                                           .enabledBlockTypes(ENABLED_BLOCKS)
                                           .extensions(EXTENSIONS)
                                           .customBlockParserFactory(new MdxBlockFactory())
                                           .build();
    
    public static List<Component> parseMarkdownToOwoComponents(String markdown, String wikiId, Identifier currentPath, Predicate<String> linkHandler) {
        
        // some html blocks are not supported, and are removed to allow the markdown parser to actually work
        for (var toRemove : removedLines) {
            markdown = markdown.replace(toRemove, "");
        }
        
        var document = PARSER.parse(markdown);
        var yamlVisitor = new YamlFrontMatterVisitor();
        document.accept(yamlVisitor);
        
        var frontMatter = parseFrontmatter(markdown);
        
        var visitor = new OwoMarkdownVisitor(linkHandler, wikiId, currentPath);
        document.accept(visitor);
        
        var components = new ArrayList<Component>();
        components.add(getTitlePanel(linkHandler, frontMatter, currentPath));
        
        if (frontMatter.containsKey("id")) {
            var gameId = frontMatter.get("id");
            var id = Identifier.of(gameId);
            if (Registries.ITEM.containsId(id) || Registries.BLOCK.containsId(id))
                components.add(createPropertiesUI(ContentProperties.getProperties(gameId)));
        }
        
        components.addAll(visitor.getResultComponents());
        
        return components;
    }
    
    private static class OwoMarkdownVisitor extends AbstractVisitor {
        
        private final Predicate<String> linkHandler;
        private final String wikiId;
        private final Identifier contentPath;
        
        private List<Component> components = new ArrayList<>();
        private MutableText buffer = Text.empty();
        private Style currentStyle = Style.EMPTY;
        private int currentIndentation = 0;
        
        private OwoMarkdownVisitor(Predicate<String> linkHandler, String wikiId, Identifier contentPath) {
            this.linkHandler = linkHandler;
            this.wikiId = wikiId;
            this.contentPath = contentPath;
        }
        
        public List<Component> getResultComponents() {
            return components;
        }
        
        private void flushBuffer() {
            if (buffer != null && !buffer.getString().isEmpty()) {
                var label = new ScalableLabelComponent(buffer, linkHandler);
                label.lineHeight(10);
                label.margins(Insets.of(0, 5, currentIndentation * 6, 0));
                components.add(label);
                buffer = Text.empty();
                currentIndentation = 0;
            }
        }
        
        @Override
        public void visit(Paragraph paragraph) {
            
            // visit all content (fills the buffer)
            visitChildren(paragraph);
            
            flushBuffer();
            
        }
        
        @Override
        public void visit(Heading heading) {
            buffer = Text.empty();
            
            var oldStyle = currentStyle;
            currentStyle = currentStyle.withColor(Formatting.GRAY);
            
            visitChildren(heading);
            
            currentStyle = oldStyle;    // reset
            
            var label = new ScalableLabelComponent(buffer, linkHandler);
            label.scale = Math.max(1.0f, 2.0f - (heading.getLevel() * 0.2f));   // calculate scale based on heading level
            label.margins(Insets.top(10).withBottom(5));
            
            components.add(label);
            buffer = Text.empty();
        }
        
        @Override
        public void visit(FencedCodeBlock codeBlock) {
            
            flushBuffer();
            
            // dark panel for code. Children are not visited, so no formatting is applied to code
            var panel = Containers.verticalFlow(Sizing.fill(), Sizing.content());
            panel.surface(ORACLE_PANEL_DARK);
            panel.padding(Insets.of(6));
            panel.margins(Insets.bottom(5));
            
            var text = Text.literal(codeBlock.getLiteral()).formatted(Formatting.GRAY);
            panel.child(Components.label(text));
            
            components.add(panel);
        }
        
        @Override
        public void visit(BulletList bulletList) {
            // a bullet list is created by adding bullets/changes to the children
            visitChildren(bulletList);
        }
        
        @Override
        public void visit(OrderedList orderedList) {
            // an ordered list is created by adding numbers to the children
            visitChildren(orderedList);
        }
        
        @Override
        public void visit(ListItem listItem) {
            
            var parent = listItem.getParent();
            
            // because listItem.getContentIndent() is always 0?
            var depth = 0;
            var ancestor = parent;
            while (ancestor instanceof ListBlock || ancestor instanceof ListItem) {
                if (ancestor instanceof ListBlock) depth++;
                ancestor = ancestor.getParent();
            }
            
            this.currentIndentation = depth - 1;
            
            if (parent instanceof BulletList) {
                buffer.append(Text.literal("• ").formatted(Formatting.DARK_GRAY)); // Bullet point
            } else if (parent instanceof OrderedList orderedList) { // i havent found a better way for this yet
                var index = 1;
                var sibling = listItem.getPrevious();
                while (sibling != null) {
                    if (sibling instanceof ListItem) index++;
                    sibling = sibling.getPrevious();
                }
                
                int displayNumber = orderedList.getStartNumber() + index - 1;
                buffer.append(Text.literal(displayNumber + ". ").formatted(Formatting.DARK_GRAY));
            }
            
            
            visitChildren(listItem);
            
            // shouldnt really be needed, but just to be safe
            flushBuffer();
            this.currentIndentation = 0;
        }
        
        @Override
        public void visit(CustomBlock customBlock) {
            
            if (customBlock instanceof MdxComponentBlock.CraftingRecipeBlock recipe) {
                components.add(createRecipeUI(recipe.slots, recipe.result, recipe.count));
            } else if (customBlock instanceof MdxComponentBlock.AssetBlock image) {
                components.add(createImageUI(image.location, image.width, this.wikiId, image.isModAsset()));
            } else if (customBlock instanceof MdxComponentBlock.CalloutBlock callout) {
                // this is a bit more complicated, since its a container. Capture children by overriding the component list
                
                var oldComponents = this.components;
                var innerComponents = new ArrayList<Component>();
                this.components = innerComponents;
                
                visitChildren(callout);
                flushBuffer();
                
                this.components = oldComponents;
                components.add(createCalloutUI(callout.variant, innerComponents));
                
            }
            
            // todo handle "PrefabObtainingBlock", whatever that is
        }
        
        @Override
        public void visit(Image image) {
            
            // flush existing text
            flushBuffer();
            
            components.add(createImageUI(image.getDestination(), "60%", wikiId, true));
        }
        
        // inline nodes
        @Override
        public void visit(org.commonmark.node.Text text) {
            if (buffer != null) {
                buffer.append(Text.literal(text.getLiteral()).setStyle(currentStyle));
            }
        }
        
        @Override
        public void visit(StrongEmphasis strongEmphasis) {
            var old = currentStyle;
            currentStyle = currentStyle.withBold(true);
            visitChildren(strongEmphasis);
            currentStyle = old;
        }
        
        @Override
        public void visit(Emphasis emphasis) {
            var old = currentStyle;
            currentStyle = currentStyle.withItalic(true);
            visitChildren(emphasis);
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
            
            // visit children to allow formatting
            visitChildren(link);
            
            currentStyle = old;
        }
        
        @Override
        public void visit(Code inlineCode) {
            // inline backticks: `some code`. Not calling visitChildren to avoid any further formatting inside
            if (buffer != null) {
                var codeText = Text.literal(inlineCode.getLiteral())
                                 .formatted(Formatting.RED);
                
                buffer.append(codeText);
            }
        }
        
        @Override
        public void visit(SoftLineBreak softLineBreak) {
            if (buffer != null)
                buffer.append(Text.literal(" "));
        }
        
        @Override
        public void visit(HardLineBreak hardLineBreak) {
            if (buffer != null)
                buffer.append(Text.literal("\n"));
        }
    }
    
    public static String getLinkText(String link, String activeWikiId, Identifier sourceEntryPath) {
        
        var linkTarget = getLinkTarget(link, activeWikiId, sourceEntryPath);
        if (linkTarget == null) return "<invalid link>";
        
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var resourceCandidate = resourceManager.getResource(linkTarget);
        if (resourceCandidate.isEmpty()) {
            return "<invalid link>";
        }
        
        try {
            var fileContent = new String(resourceCandidate.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var frontMatter = parseFrontmatter(fileContent);
            return getTitle(frontMatter, linkTarget);
        } catch (IOException e) {
            Oracle.LOGGER.warn("Unable to load file content to get link title: {}, {}", linkTarget, e);
            return "<invalid link>";
        }
        
    }
    
    @Nullable
    public static Identifier getLinkTarget(String link, String activeWikiId, Identifier sourceEntryPath) {
        
        Identifier targetFile = null;
        
        if (link.startsWith("@") || link.contains(":")) {  // reference content by id
            var id = link.startsWith("@") ? link.substring(1) : link;
            
            if (OracleClient.CONTENT_ID_MAP.containsKey(id)) {
                targetFile = OracleClient.CONTENT_ID_MAP.get(id);
            }
            
        } else if (link.startsWith("$")) {  // references docs by path
            var newPathString = "books/" + activeWikiId + "/" + link.substring(1);
            
            if (!newPathString.endsWith(".mdx")) {
                newPathString += ".mdx";
            }
            
            targetFile = Identifier.of(Oracle.MOD_ID, newPathString);
        } else {
            // default relative links
            var newPathString = OracleScreen.parsePathLink(link, sourceEntryPath);
            
            // add extension if missing. Theoretically links without file ending would be valid/used in some cases
            if (!newPathString.endsWith(".mdx")) {
                newPathString += ".mdx";
            }
            
            targetFile = Identifier.of(Oracle.MOD_ID, newPathString);
        }
        
        return targetFile;
    }
    
    // tries to get the title from either the title field, or them item name from the id field, or just the id
    public static String getTitle(Map<String, String> frontMatter, Identifier pagePath) {
        
        if (frontMatter.containsKey("title")) {
            return frontMatter.get("title");
        } else if (frontMatter.containsKey("id")) {
            var item = frontMatter.get("id");
            if (Identifier.validate(item).isSuccess() && Registries.ITEM.containsId(Identifier.of(item))) {
                return I18n.translate(Registries.ITEM.get(Identifier.of(item)).getTranslationKey());
            } else {
                return item;
            }
        } else return OracleScreen.PAGE_FALLBACK_NAMES.getOrDefault(pagePath, "No title found");
        
    }
    
    private static FlowLayout getTitlePanel(Predicate<String> linkHandler, Map<String, String> frontMatter, Identifier pageId) {
        
        var combinedPanel = Containers.horizontalFlow(Sizing.fill(), Sizing.content());
        combinedPanel.margins(Insets.of(2, 10, 0, 0));
        
        var titlePanel = Containers.horizontalFlow(Sizing.content(0), Sizing.content(0));
        titlePanel.padding(Insets.of(10, 10, 10, 10));
        titlePanel.surface(ORACLE_PANEL);
        combinedPanel.child(titlePanel.positioning(Positioning.absolute(48 + 6, 7)));
        
        var title = getTitle(frontMatter, pageId);
        var iconId = frontMatter.getOrDefault("icon", "");
        if (iconId.isBlank()) iconId = frontMatter.getOrDefault("id", "");
        if (Identifier.validate(iconId).isSuccess()) {
            // try find item
            if (Registries.ITEM.containsId(Identifier.of(iconId))) {
                var itemDisplay = new ItemStack(Registries.ITEM.get(Identifier.of(iconId)));
                var itemComponent = Components.item(itemDisplay);
                itemComponent.sizing(Sizing.fixed(48));
                
                var itemPanel = Containers.horizontalFlow(Sizing.content(0), Sizing.content(0));
                itemPanel.padding(Insets.of(6, 6, 6, 6));
                itemPanel.surface(ORACLE_PANEL);
                itemPanel.child(itemComponent);
                combinedPanel.child(itemPanel);
            } else {
                titlePanel.positioning(Positioning.layout());
            }
        } else {
            titlePanel.positioning(Positioning.layout());
        }
        
        var titleLabel = new ScalableLabelComponent(Text.literal(title).formatted(Formatting.DARK_GRAY), linkHandler);
        titleLabel.scale = 2f;
        titlePanel.child(titleLabel);
        
        var spacedPanel = Containers.horizontalFlow(Sizing.fill(), Sizing.content());
        spacedPanel.child(combinedPanel);
        spacedPanel.margins(Insets.of(15, 5, 2, 2));
        return spacedPanel;
    }
    
    public static Component createRecipeUI(List<String> inputs, String resultId, int resultCount) {
        if (inputs.size() != 9) {
            return Components.label(Text.literal("Invalid crafting recipe data: expected 9 inputs").formatted(Formatting.RED));
        }
        
        var panel = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        panel.surface(ORACLE_PANEL);
        panel.horizontalAlignment(HorizontalAlignment.CENTER);
        panel.verticalAlignment(VerticalAlignment.CENTER);
        
        var inputGrid = Containers.grid(Sizing.content(), Sizing.content(), 3, 3);
        inputGrid.padding(Insets.of(3));
        inputGrid.margins(Insets.of(3));
        inputGrid.positioning(Positioning.relative(0, 0));
        
        var backgroundGrid = Containers.grid(Sizing.content(), Sizing.content(), 3, 3);
        backgroundGrid.padding(Insets.of(3));
        backgroundGrid.margins(Insets.of(3));
        
        for (var i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            var id = Identifier.of(input);
            
            if (!input.equals("minecraft:air") && !input.isEmpty() && Registries.ITEM.containsId(id)) {
                var itemstack = new ItemStack(Registries.ITEM.get(id));
                var itemComponent = Components.item(itemstack);
                itemComponent.setTooltipFromStack(true);
                
                var row = i / 3;
                var column = i % 3;
                
                inputGrid.child(itemComponent, row, column);
            }
            
            backgroundGrid.child(getItemFrame(), i / 3, i % 3);
        }
        
        var arrow = Components.texture(Identifier.of(Oracle.MOD_ID, "textures/arrow_empty.png"), 0, 0, 29, 16, 29, 16);
        arrow.margins(Insets.of(5));
        
        var resultIdObj = Identifier.of(resultId);
        var resultStack = Registries.ITEM.containsId(resultIdObj)
                            ? new ItemStack(Registries.ITEM.get(resultIdObj), resultCount)
                            : ItemStack.EMPTY;
        
        var result = Components.item(resultStack);
        result.setTooltipFromStack(true);
        result.margins(Insets.of(5));
        
        var resultFrame = getItemFrame().positioning(Positioning.relative(100, 50));
        resultFrame.margins(Insets.of(5));
        
        panel.child(backgroundGrid);
        panel.child(inputGrid);
        panel.child(arrow);
        panel.child(result);
        panel.child(resultFrame);
        
        return panel;
    }
    
    public static Component createCalloutUI(String variant, List<Component> children) {
        // wrapper for the content
        var contentFlow = Containers.verticalFlow(Sizing.fill(70), Sizing.content());
        contentFlow.horizontalAlignment(HorizontalAlignment.CENTER);
        
        for (var child : children) {
            child.horizontalSizing(Sizing.fill()); // Ensure text wraps
            if (child instanceof LabelComponent label) {
                label.horizontalTextAlignment(HorizontalAlignment.CENTER);
                label.color(Color.ofRgb(5592405));
            }
            contentFlow.child(child);
        }
        
        var contentContainer = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        contentContainer.padding(Insets.of(6, 6, 8, 8));
        contentContainer.margins(Insets.of(15, 8, 10, 0));
        contentContainer.surface(ORACLE_PANEL);
        contentContainer.child(contentFlow);
        
        var titleText = StringUtils.capitalize(variant);
        var titleLabel = Components.label(Text.literal(titleText));
        
        var titleContainer = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        titleContainer.padding(Insets.of(6, 5, 8, 6));
        titleContainer.positioning(Positioning.absolute(0, 0));
        titleContainer.surface(ORACLE_PANEL_PRESSED);
        titleContainer.child(titleLabel);
        
        var combinedContainer = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        combinedContainer.child(contentContainer);
        combinedContainer.child(titleContainer);
        combinedContainer.margins(Insets.bottom(4));
        
        return combinedContainer;
    }
    
    public static Component createImageUI(String location, String widthSource, String wikiId, boolean isModAsset) {
        var width = convertImageWidth(widthSource);
        if (width <= 0) width = 0.5f;
        
        if (location.startsWith("@")) {
            location = location.substring(1);
        }
        
        // case 1: ingame item
        var itemIdCandidate = Identifier.of(location);
        if (Registries.ITEM.containsId(itemIdCandidate)) {
            if (width == 0.5f) width = 0.1f;
            var itemComponent = Components.item(new ItemStack(Registries.ITEM.get(itemIdCandidate)));
            itemComponent.setTooltipFromStack(true);
            itemComponent.verticalSizing(Sizing.fixed((int) (width * 100)));
            return itemComponent;
        }
        
        // case 2: texture path
        Identifier searchPath;
        if (isModAsset) {
            // Logic for <ModAsset>: Expects "modid:path/to/image"
            // File location: books/{wikiId}/.assets/{modid}/{path}.png
            // if no mod id is found, it uses the existing book id of the article
            var parts = location.split(":", 2);
            var imageModId = parts.length > 0 ? parts[0] : wikiId;
            var imagePath = parts.length > 1 ? parts[1] : location;
            searchPath = Identifier.of(Oracle.MOD_ID, ROOT_DIR + "/" + wikiId + "/.assets/" + imageModId + "/" + imagePath + ".png");
        } else {
            // Logic for <Asset>: Expects "path/to/image" relative to current book assets
            searchPath = Identifier.of(Oracle.MOD_ID, ROOT_DIR + "/" + wikiId + "/.assets/" + wikiId + "/" + location + ".png");
        }
        
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var resource = resourceManager.getResource(searchPath);
        
        if (resource.isEmpty()) {
            return Components.label(Text.literal("Image not found: " + searchPath).formatted(Formatting.RED));
        }
        
        try {
            var image = NativeImage.read(resource.get().getInputStream());
            var result = Components.texture(searchPath, 0, 0, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());
            result.verticalSizing(Sizing.fixed((int) (width * 100)));
            return result;
        } catch (IOException e) {
            return Components.label(Text.literal("Error reading image: " + location).formatted(Formatting.RED));
        }
    }
    
    // creates a grid that contains the properties
    private static Component createPropertiesUI(Map<String, Text> properties) {
        
        var outer = Containers.verticalFlow(Sizing.fill(80), Sizing.content());
        outer.surface(ORACLE_PANEL_DARK);
        outer.padding(Insets.of(10));
        outer.margins(Insets.bottom(15));
        outer.horizontalAlignment(HorizontalAlignment.CENTER);
        
        // title
        outer.child(Components.label(Text.literal("Details").formatted(Formatting.BOLD, Formatting.GRAY)).margins(Insets.bottom(8)));
        
        var grid = Containers.grid(Sizing.fill(), Sizing.content(), properties.size(), 2);
        
        int row = 0;
        for (var entry : properties.entrySet()) {
            var key = Components.label(Text.literal(entry.getKey()).formatted(Formatting.GOLD));
            var value = Components.label(entry.getValue());
            value.horizontalSizing(Sizing.fill(45));
            
            // key on left, property on right
            grid.child(key.horizontalTextAlignment(HorizontalAlignment.LEFT), row, 0);
            grid.child(value.horizontalTextAlignment(HorizontalAlignment.RIGHT), row, 1);
            row++;
        }
        
        outer.child(grid);
        return outer;
    }
    
    public static Map<String, String> parseFrontmatter(String markdown) {
        
        var document = PARSER.parse(markdown);
        var yamlVisitor = new YamlFrontMatterVisitor();
        document.accept(yamlVisitor);
        
        // map of key to values (e.g. first one or for lists multiple values)
        var frontmatter = yamlVisitor.getData();
        
        var simpleFrontMatter = new HashMap<String, String>();
        for (var pair : frontmatter.entrySet()) {
            simpleFrontMatter.put(pair.getKey(), pair.getValue().getFirst().trim());
        }
        
        return simpleFrontMatter;
    }
    
    public static Component getItemFrame() {
        return Components.texture(ITEM_SLOT, 0, 0, 16, 16, 16, 16).sizing(Sizing.fixed(16));
    }
    
    public static float convertImageWidth(String input) {
        if (input == null || input.isEmpty()) {
            return 0.0f;
        }
        
        var trimmedInput = input.trim(); // Remove leading/trailing whitespace
        
        if (trimmedInput.endsWith("%")) {
            try {
                var percentageString = trimmedInput.substring(0, trimmedInput.length() - 1); // Remove the "%"
                var percentage = Integer.parseInt(percentageString);
                return percentage / 100.0f; // Normalize to 0.0f - 1.0f range
            } catch (NumberFormatException e) {
                System.err.println("Error parsing percentage value: " + input);
                return 0.0f; // Or handle parsing errors as needed
            }
        } else if (trimmedInput.startsWith("{") && trimmedInput.endsWith("}")) {
            try {
                var numberString = trimmedInput.substring(1, trimmedInput.length() - 1); // Remove "{" and "}"
                var number = Integer.parseInt(numberString);
                return number / 1000.0f; // Normalize to 0.0f - 1.0f range
            } catch (NumberFormatException e) {
                System.err.println("Error parsing braced number value: " + input);
                return 0.0f; // Or handle parsing errors as needed
            }
        } else if (StringUtils.isNumeric(trimmedInput)) {
            try {
                var number = Integer.parseInt(trimmedInput);
                return number / 1000.0f; // Normalize to 0.0f - 1.0f range
            } catch (NumberFormatException e) {
                System.err.println("Error parsing braced number value: " + input);
                return 0.0f; // Or handle parsing errors as needed
            }
        } else {
            System.err.println("Invalid input format: " + input);
            return 0.0f; // Or handle invalid formats as needed
        }
    }
    
}