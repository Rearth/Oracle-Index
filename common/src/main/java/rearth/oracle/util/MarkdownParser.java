package rearth.oracle.util;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.util.NinePatchTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.*;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.jsoup.Jsoup;
import rearth.oracle.Oracle;
import rearth.oracle.ui.components.ScalableLabelComponent;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// a very basic and primitive (and hacky) ghetto markdown to owo lib parser
public class MarkdownParser {
    
    private static final String[] removedLines = new String[]{"<center>", "</center>", "<div>", "</div>", "<span>", "</span>"};
    
    public static final Identifier ITEM_SLOT = Identifier.of(Oracle.MOD_ID, "textures/item_cell.png");
    
    public static Surface ORACLE_PANEL = (context, component) -> NinePatchTexture.draw(Identifier.of(Oracle.MOD_ID, "bedrock_panel"), context, component);
    public static Surface ORACLE_PANEL_HOVER = (context, component) -> NinePatchTexture.draw(Identifier.of(Oracle.MOD_ID, "bedrock_panel_hover"), context, component);
    public static Surface ORACLE_PANEL_PRESSED = (context, component) -> NinePatchTexture.draw(Identifier.of(Oracle.MOD_ID, "bedrock_panel_pressed"), context, component);
    public static Surface ORACLE_PANEL_DARK = (context, component) -> NinePatchTexture.draw(Identifier.of(Oracle.MOD_ID, "bedrock_panel_dark"), context, component);
    
    private static final List<Extension> EXTENSIONS = List.of(YamlFrontMatterExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).customBlockParserFactory(new MdxBlockFactory()).build();
    
    public static List<Component> parseMarkdownToOwoComponents(String markdown, String bookId, Predicate<String> linkHandler) {
        
        // some html blocks are not supported, and are removed to allow the markdown parser to actually work
        for (var toRemove : removedLines) {
            markdown = markdown.replace(toRemove, "");
        }
        
        var document = PARSER.parse(markdown);
        var yamlVisitor = new YamlFrontMatterVisitor();
        document.accept(yamlVisitor);
        
        // map of key to values (e.g. first one or for lists multiple values)
        var frontmatter = yamlVisitor.getData();
        
        var simpleFrontMatter = new HashMap<String, String>();
        for (var pair : frontmatter.entrySet()) {
            simpleFrontMatter.put(pair.getKey(), pair.getValue().getFirst());
        }
        
        var visitor = new OwoMarkdownVisitor(linkHandler);
        document.accept(visitor);
        
        var components = new ArrayList<Component>();
        components.add(getTitlePanel(linkHandler, simpleFrontMatter));
        components.addAll(visitor.getResultComponents());
        
        return components;
    }
    
    private static class OwoMarkdownVisitor extends AbstractVisitor {
        
        private final List<Component> components = new ArrayList<>();
        private final Predicate<String> linkHandler;
        
        private MutableText buffer;
        private Style currentStyle = Style.EMPTY;
        
        private OwoMarkdownVisitor(Predicate<String> linkHandler) {
            this.linkHandler = linkHandler;
        }
        
        public List<Component> getResultComponents() {
            return components;
        }
        
        private void flushBuffer() {
            if (buffer != null && !buffer.getString().isEmpty()) {
                var label = new ScalableLabelComponent(buffer, linkHandler);
                label.lineHeight(10);
                label.margins(Insets.bottom(5));
                components.add(label);
                buffer = Text.empty();
            }
        }
        
        @Override
        public void visit(Paragraph paragraph) {
            
            // begin a new paragraph
            buffer = Text.empty();
            // visit all content (fills the buffer)
            visitChildren(paragraph);
            
            flushBuffer();
            
        }
        
        @Override
        public void visit(Heading heading) {
            buffer = Text.empty();
            
            var oldStyle = currentStyle;
            currentStyle = currentStyle.withBold(true).withColor(Formatting.GOLD);
            
            visitChildren(heading);
            
            currentStyle = oldStyle;    // reset
            
            var label = new ScalableLabelComponent(buffer, linkHandler);
            label.scale = Math.max(1.0f, 2.0f - (heading.getLevel() * 0.2f));   // calculate scale based on heading level
            label.margins(Insets.top(10).withBottom(5));
            
            components.add(label);
            buffer = null;
        }
        
        @Override
        public void visit(FencedCodeBlock codeBlock) {
            
            flushBuffer();
            
            // dark panel for code. Children are not visited, so no formatting is applied to code
            var panel = Containers.verticalFlow(Sizing.fill(), Sizing.content());
            panel.surface(ORACLE_PANEL_DARK);
            panel.padding(Insets.of(6));
            panel.margins(Insets.bottom(5));
            
            var text = Text.literal(codeBlock.getLiteral().trim()).formatted(Formatting.GRAY);
            panel.child(Components.label(text));
            
            components.add(panel);
        }
        
        @Override
        public void visit(BulletList bulletList) {
            // a bullet list is created by adding bullets/changes to the children
            visitChildren(bulletList);
        }
        
        @Override
        public void visit(ListItem listItem) {
            buffer = Text.empty();
            buffer.append(Text.literal("• ").formatted(Formatting.DARK_GRAY)); // Bullet point
            
            visitChildren(listItem);
            
            var label = new ScalableLabelComponent(buffer, linkHandler);
            label.margins(Insets.left(10).withBottom(2)); // Indent
            components.add(label);
            buffer = null;
        }
        
        @Override
        public void visit(CustomBlock customBlock) {
            
            if (customBlock instanceof MdxComponentBlock.CraftingRecipeBlock craftingRecipeBlock) {
                Oracle.LOGGER.info("crafting html detected: {}", craftingRecipeBlock);
            } else {
                Oracle.LOGGER.info("Custom block: {}", customBlock);
            }
        }
        
        @Override
        public void visit(Image image) {
            
            // flush existing text
            flushBuffer();
            
            Oracle.LOGGER.warn("detected non-asset image tag: {}", image.getDestination());
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
    }
    
    private static FlowLayout getTitlePanel(Predicate<String> linkHandler, Map<String, String> frontMatter) {
        
        var combinedPanel = Containers.horizontalFlow(Sizing.fill(), Sizing.content());
        combinedPanel.margins(Insets.of(2, 10, 0, 0));
        
        var titlePanel = Containers.horizontalFlow(Sizing.content(0), Sizing.content(0));
        titlePanel.padding(Insets.of(10, 10, 10, 10));
        titlePanel.surface(ORACLE_PANEL);
        combinedPanel.child(titlePanel.positioning(Positioning.absolute(48 + 6, 7)));
        
        var title = frontMatter.getOrDefault("title", "Title not found in Frontmatter");
        var iconId = frontMatter.getOrDefault("icon", "");
        if (!iconId.isEmpty()) {
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
    
    public static Map<String, String> parseFrontmatter(String markdownContent) {
        var frontmatter = new HashMap<String, String>();
        if (!markdownContent.startsWith("---")) {
            return frontmatter; // No frontmatter found
        }
        
        var endDelimiterIndex = markdownContent.indexOf("---", 3); // Start searching after the first ---
        if (endDelimiterIndex == -1) {
            return frontmatter; // No closing delimiter found
        }
        
        var frontmatterContent = markdownContent.substring(4, endDelimiterIndex); // Exclude delimiters
        var lines = frontmatterContent.lines().toList();
        
        for (var line : lines) {
            var parts = line.split(":", 2); // Split only at the first ':'
            if (parts.length == 2) {
                var key = parts[0].trim();
                var value = parts[1].trim();
                frontmatter.put(key, value);
            }
        }
        return frontmatter;
    }
    
    public static String removeFrontmatter(String markdownContent) {
        if (!markdownContent.startsWith("---")) {
            return markdownContent; // No frontmatter, return original
        }
        
        var endDelimiterIndex = markdownContent.indexOf("---", 3); // Start searching after the first ---
        if (endDelimiterIndex == -1) {
            return markdownContent; // No closing delimiter, return original
        }
        
        return markdownContent.substring(endDelimiterIndex + 3).trim(); // +4 to also remove "---" and "\n"
    }
    
    
    private static List<String> splitIntoParagraphs(String markdownContent) {
        var lines = markdownContent.lines().toList();
        var paragraphList = new ArrayList<String>();
        
        var currentParagraph = new StringBuilder();
        var inCodeBlock = false;
        for (var s : lines) {
            var line = s.trim();
            
            var isSkipped = Arrays.stream(removedLines).anyMatch(line::startsWith);
            var isSkippedDiv = line.startsWith("<div onlineOnly=\"true\"");
            if (isSkipped && !inCodeBlock && !isSkippedDiv) continue;
            
            var newCodeBlock = line.startsWith("```");
            
            if (inCodeBlock && newCodeBlock) {
                inCodeBlock = false;    // end existing code block
            } else if (newCodeBlock) {
                inCodeBlock = true; // begin code block, begin new paragraph
                paragraphList.add(currentParagraph.toString());
                currentParagraph = new StringBuilder();
            }
            
            var isHeading = line.startsWith("#");
            var isListing = line.matches("[0-9]+\\.\\s.+");
            var isUnorderedList = line.matches("-\\s.+");
            var isWeirdList = line.matches("•\\s.+");
            var isHtml = line.matches("<[a-zA-Z]+");
            
            if (isHeading || isListing || isUnorderedList || isWeirdList || isHtml) {
                paragraphList.add(currentParagraph.toString());
                currentParagraph = new StringBuilder();
                
                if (isHeading) {    // headings get their own paragraph, lists/other components not
                    paragraphList.add(line);
                } else {
                    currentParagraph.append(line).append(" ");
                }
                continue;
            }
            
            var isSeparator = line.isEmpty();
            if (isSeparator) {
                paragraphList.add(currentParagraph.toString());
                currentParagraph = new StringBuilder();
            } else if (inCodeBlock) {
                currentParagraph.append(line).append("\n");
            } else {
                currentParagraph.append(line).append(" ");
            }
            
            var containsCodeBlock = line.contains("```");
            if (!newCodeBlock && containsCodeBlock) {
                inCodeBlock = false;
            }
        }
        
        if (!currentParagraph.isEmpty() && !currentParagraph.toString().startsWith("<div onlineOnly=\"true\""))
            paragraphList.add(currentParagraph.toString());
        
        
        return paragraphList;
    }
    
    private static Component parseImageParagraph(String paragraphString, String bookId, boolean modAsset) {
        
        var tagName = modAsset ? "ModAsset" : "Asset";
        
        var doc = Jsoup.parseBodyFragment(paragraphString);
        var element = doc.selectFirst(tagName);
        
        if (element != null) {
            var location = element.attr("location");
            var widthSource = element.attr("width");
            var width = convertWidthStringToFloat(widthSource);
            if (width <= 0) width = 0.5f;   // default to 50% width
            
            var itemIdCandidate = Identifier.of(location);
            if (Registries.ITEM.containsId(itemIdCandidate)) {
                if (width == 0.5f) width = 0.1f;    // make items smaller
                var imageComponent = Components.item(new ItemStack(Registries.ITEM.get(itemIdCandidate)));
                imageComponent.setTooltipFromStack(true);
                imageComponent.verticalSizing(Sizing.fixed((int) (width * 100)));   // width is set as a way to get the desired width in the layout methods
                return imageComponent;
            }
            
            var imageModId = location.split(":")[0];
            var imageModPath = location.split(":")[1];
            var searchPath = Identifier.of(Oracle.MOD_ID, "books/" + bookId + "/.assets/" + bookId + "/" + imageModPath + ".png");
            
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(searchPath);
            if (resource.isEmpty()) {
                return Components.label(Text.literal("Image not found: " + searchPath).formatted(Formatting.RED));
            }
            
            try {
                var image = NativeImage.read(resource.get().getInputStream());
                var result = Components.texture(searchPath, 0, 0, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());
                result.verticalSizing(Sizing.fixed((int) (width * 100)));   // width is set as a way to get the desired width in the layout methods
                return result;
            } catch (IOException e) {
                return Components.label(Text.literal("Image couldn't be read: " + location + "\n" + e.getMessage().formatted(Formatting.RED)));
            }
            
        } else {
            return Components.label(Text.literal("Image path couldn't be parsed in: " + paragraphString).formatted(Formatting.RED));
        }
    }
    
    private static Component parseCalloutParagraph(String paragraphString, Predicate<String> linkHandler) {
        
        var doc = Jsoup.parseBodyFragment(paragraphString);
        var element = doc.selectFirst("Callout");
        
        if (element == null) return Components.label(Text.literal("Invalid Callout"));
        
        var calloutText = element.text();
        var calloutVariant = element.attr("variant");
        
        var contentLabel = parseParagraphToLabel(calloutText, linkHandler);
        contentLabel.horizontalSizing(Sizing.fill(70));
        
        if (contentLabel instanceof LabelComponent labelComponent) {
            labelComponent.horizontalTextAlignment(HorizontalAlignment.CENTER);
            labelComponent.color(Color.ofRgb(5592405));
        }
        var titleLabel = Components.label(Text.literal(StringUtils.capitalize((calloutVariant))));
        
        var contentContainer = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        contentContainer.padding(Insets.of(6, 8, 8, 8));
        contentContainer.margins(Insets.of(15, 0, 10, 0));
        contentContainer.surface(ORACLE_PANEL);
        contentContainer.child(contentLabel);
        var titleContainer = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        titleContainer.padding(Insets.of(6, 5, 8, 6));
        titleContainer.positioning(Positioning.absolute(0, 0));
        titleContainer.surface(ORACLE_PANEL_PRESSED);
        titleContainer.child(titleLabel);
        
        var combinedContainer = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        combinedContainer.child(contentContainer);
        combinedContainer.child(titleContainer);
        
        return combinedContainer;
    }
    
    private static Component parseRecipeParagraph(String paragraph) {
        
        var doc = Jsoup.parseBodyFragment(paragraph.replace("{[", "\"{[").replace("]}", "]}\""));
        var element = doc.selectFirst("CraftingRecipe");
        
        if (element == null) return Components.label(Text.literal("Invalid recipe, unable to parse html"));
        
        var recipeInputs = element.attr("slots");
        var recipeResult = element.attr("result");
        var recipeResultCount = element.attr("count").replace("{", "").replace("}", "");
        int resultCount = 1;
        try {
            resultCount = Integer.parseInt(recipeResultCount);
        } catch (NumberFormatException ignored) {
        }
        
        var recipeInputItems = extractRecipeInputs(recipeInputs);
        if (recipeInputItems.size() != 9)
            return Components.label(Text.literal("Invalid recipe, unable to parse 9 ingredients"));
        
        var panel = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        panel.surface(ORACLE_PANEL);
        panel.horizontalAlignment(HorizontalAlignment.CENTER);
        panel.verticalAlignment(VerticalAlignment.CENTER);
        
        var inputGrid = Containers.grid(Sizing.content(), Sizing.content(), 3, 3);
        inputGrid.padding(Insets.of(3));
        inputGrid.margins(Insets.of(3));
        var backgroundGrid = Containers.grid(Sizing.content(), Sizing.content(), 3, 3);
        backgroundGrid.padding(Insets.of(3));
        backgroundGrid.margins(Insets.of(3));
        backgroundGrid.positioning(Positioning.relative(0, 0));
        for (int i = 0; i < recipeInputItems.size(); i++) {
            var input = recipeInputItems.get(i);
            var id = Identifier.of(input);
            var itemstack = new ItemStack(Registries.ITEM.get(id));
            var itemComponent = Components.item(itemstack);
            itemComponent.setTooltipFromStack(true);
            
            var row = i % 3;
            var column = i / 3;
            
            backgroundGrid.child(getItemFrame(), column, row);
            inputGrid.child(itemComponent, column, row);
        }
        
        var arrow = Components.texture(Identifier.of(Oracle.MOD_ID, "textures/arrow_empty.png"), 0, 0, 29, 16, 29, 16);
        arrow.margins(Insets.of(5));
        var result = Components.item(new ItemStack(Registries.ITEM.get(Identifier.of(recipeResult)), resultCount));
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
    
    public static List<String> extractRecipeInputs(String input) {
        List<String> resultList = new ArrayList<>();
        
        var trimmedInput = input.trim();
        if (trimmedInput.startsWith("{[") && trimmedInput.endsWith("]}")) {
            trimmedInput = trimmedInput.substring(2, trimmedInput.length() - 2).trim();
        } else {
            System.err.println("Input string does not have the expected format.");
            return resultList; // Or throw an exception if you prefer
        }
        
        var elements = trimmedInput.split(",");
        
        for (var element : elements) {
            var trimmedElement = element.trim();
            
            // Remove single quotes if present
            if (trimmedElement.startsWith("'") && trimmedElement.endsWith("'")) {
                trimmedElement = trimmedElement.substring(1, trimmedElement.length() - 1);
            }
            
            resultList.add(trimmedElement);
        }
        
        return resultList;
    }
    
    public static Component getItemFrame() {
        return Components.texture(ITEM_SLOT, 0, 0, 16, 16, 16, 16).sizing(Sizing.fixed(16));
    }
    
    private static Component parseParagraphToLabel(String paragraphString, Predicate<String> linkHandler) {
        var paragraphText = Text.empty();
        var index = 0;
        var processedParagraphString = paragraphString;
        
        var isCodeBlock = paragraphString.startsWith("```");
        if (isCodeBlock)
            processedParagraphString = processedParagraphString.replace("```", "").trim();
        
        // process headings
        var headingLevel = getHeadingLevel(paragraphString);
        if (headingLevel > 0 && !isCodeBlock) {
            var headingPattern = Pattern.compile("^(#+)\\s*(.+)");
            var headingMatcher = headingPattern.matcher(paragraphString);
            if (headingMatcher.find()) {
                processedParagraphString = headingMatcher.group(2).trim(); // Extract the heading text without '#' and spaces
            }
        }
        
        while (index < processedParagraphString.length()) {
            
            // Check for links: [link text](url)
            var linkMatcher = Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)").matcher(processedParagraphString);
            if (linkMatcher.find(index) && linkMatcher.start() == index && !isCodeBlock) {
                var linkText = linkMatcher.group(1);
                var url = linkMatcher.group(2);
                
                // Style for links: blue and underlined
                var linkStyle = Style.EMPTY
                                  .withUnderline(true)
                                  .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                
                paragraphText.append(Text.literal(linkText).setStyle(linkStyle));
                index += linkMatcher.group(0).length();
                continue;
            }
            
            // Check for bold text: **text** or __text__
            var boldMatcher = Pattern.compile("\\*\\*([^*]+)\\*\\*|__([^_]+)__").matcher(processedParagraphString);
            if (boldMatcher.find(index) && boldMatcher.start() == index && !isCodeBlock) {
                var boldText = (boldMatcher.group(1) != null) ? boldMatcher.group(1) : boldMatcher.group(2);
                paragraphText.append(Text.literal(boldText).setStyle(Style.EMPTY.withBold(true)));
                index += boldMatcher.group(0).length();
                continue;
            }
            
            // Check for italic text: *text* or _text_
            var italicMatcher = Pattern.compile("\\*([^*]+)\\*|_([^_]+)_").matcher(processedParagraphString);
            if (italicMatcher.find(index) && italicMatcher.start() == index && !isCodeBlock) {
                var italicText = (italicMatcher.group(1) != null) ? italicMatcher.group(1) : italicMatcher.group(2);
                paragraphText.append(Text.literal(italicText).setStyle(Style.EMPTY.withItalic(true)));
                index += italicMatcher.group(0).length();
                continue;
            }
            
            //Check for color (example: #FF0000 text #FFFFFF )
            var colorMatcher = Pattern.compile("#([0-9A-Fa-f]{6})\\s*([^#]+?)\\s*#([0-9A-Fa-f]{6})").matcher(processedParagraphString);
            if (colorMatcher.find(index) && colorMatcher.start() == index && !isCodeBlock) {
                var color1 = colorMatcher.group(1);
                var coloredText = colorMatcher.group(2);
                
                var textColor1 = TextColor.fromRgb(Integer.parseInt(color1, 16));
                
                var coloredTextComponent = Text.literal(coloredText).setStyle(Style.EMPTY.withColor(textColor1));
                
                paragraphText.append(coloredTextComponent);
                
                index += colorMatcher.group(0).length();
                continue;
            }
            
            // Handle regular text
            paragraphText.append(Text.literal(String.valueOf(processedParagraphString.charAt(index))));
            index++;
        }
        
        if (headingLevel > 0)
            paragraphText = paragraphText.formatted(Formatting.GRAY);
        
        var label = new ScalableLabelComponent(paragraphText, linkHandler);
        if (headingLevel > 0) {
            label.scale = 1.5f - headingLevel * 0.1f;
        }
        label.lineHeight(10);
        
        if (isCodeBlock) {
            var panel = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            panel.surface(ORACLE_PANEL_DARK);
            panel.padding(Insets.of(6));
            panel.child(label);
            label.horizontalSizing(Sizing.fill(100));
            return panel;
        }
        
        return label;
    }
    
    public static int getHeadingLevel(String paragraphString) {
        var headingMatcher = Pattern.compile("^(#+)\\s*(.+)").matcher(paragraphString.trim());
        if (headingMatcher.find()) {
            return headingMatcher.group(1).length();
        }
        return 0; // Not a heading
    }
    
    public static float convertWidthStringToFloat(String input) {
        if (input == null || input.isEmpty()) {
            return 0.0f; // Or handle null/empty input as needed, maybe throw an exception
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
        } else {
            System.err.println("Invalid input format: " + input);
            return 0.0f; // Or handle invalid formats as needed
        }
    }
    
}