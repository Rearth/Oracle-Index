package rearth.oracle.util;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Component;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jsoup.Jsoup;
import rearth.oracle.Oracle;
import rearth.oracle.ui.components.ScalableLabelComponent;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// a very basic and primitive (and hacky) ghetto markdown to owo lib parser
public class MarkdownParser {
		
		private static final String[] removedLines = new String[] {"<center>", "</center>"};
		
		public static List<Component> parseMarkdownToOwoComponents(String markdown, String bookId, Predicate<String> linkHandler) {
				var components = new ArrayList<Component>();
				
				var frontMatter = parseFrontmatter(markdown);
				var contentWithoutFrontmatter = removeFrontmatter(markdown);
				
				var title = frontMatter.getOrDefault("title", "Title not found in Frontmatter");
				var titleLabel = new ScalableLabelComponent(Text.literal(title).formatted(Formatting.BOLD), linkHandler);
				titleLabel.scale = 2.2f;
				titleLabel.color(new Color(0.6f, 0.6f, 1f));
				
				components.add(titleLabel);
				
				var paragraphs = splitIntoParagraphs(contentWithoutFrontmatter);
				var htmlTagPattern = Pattern.compile("<([a-zA-Z0-9]+)(?:\\s[^>]*)?>"); // Regex to find opening HTML tags
				
				for (var paragraph : paragraphs) {
						var trimmedParagraph = paragraph.trim();
						if (trimmedParagraph.isEmpty()) continue;
						
						var matcher = htmlTagPattern.matcher(trimmedParagraph);
						
						if (matcher.lookingAt()) { // Check if paragraph starts with an HTML tag
								var tagName = matcher.group(1); // Extract the tag name
								var tagContent = trimmedParagraph.substring(matcher.end()).trim(); // Content after the opening tag, e.g. the "variant=info" of a 'callout'
								
								// Handle specific HTML tags
								if ("Callout".equalsIgnoreCase(tagName)) {
										// components.add(parseCallout(tagContent, bookId, linkHandler));
								} else if ("ModAsset".equalsIgnoreCase(tagName)) {
										components.add(parseImageParagraph(paragraph, bookId));
								} else {
										// do nothing, other html tags are not supported (yet)
								}
						} else {
								// if not html, call normal paragraph method
								components.add(parseParagraphToLabel(trimmedParagraph, linkHandler));
						}
				}
				
				return components;
		}
		
		public static Map<String, String> parseFrontmatter(String markdownContent) {
				var frontmatter = new HashMap<String, String>();
				if (!markdownContent.startsWith("---")) {
						return frontmatter; // No frontmatter found
				}
				
				var endDelimiterIndex = markdownContent.indexOf("---" + "\n", 3); // Start searching after the first ---
				var endDelimiterIndexAlt = markdownContent.indexOf("---" + "\r\n", 3); // Start searching after the first ---
				var usedDelimitedIndex = Math.max(endDelimiterIndex, endDelimiterIndexAlt);
				if (usedDelimitedIndex == -1) {
						return frontmatter; // No closing delimiter found
				}
				
				var frontmatterContent = markdownContent.substring(4, usedDelimitedIndex); // Exclude delimiters
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
				
				var endDelimiterIndex = markdownContent.indexOf("---" + "\n", 3); // Start searching after the first ---
				var endDelimiterIndexAlt = markdownContent.indexOf("---" + "\r\n", 3); // Start searching after the first ---
				var usedDelimitedIndex = Math.max(endDelimiterIndex, endDelimiterIndexAlt);
				if (usedDelimitedIndex == -1) {
						return markdownContent; // No closing delimiter, return original
				}
				
				return markdownContent.substring(usedDelimitedIndex + 4); // +4 to also remove "---" and "\n"
		}
		
		
		private static List<String> splitIntoParagraphs(String markdownContent) {
				var lines = markdownContent.lines().toList();
				var paragraphList = new ArrayList<String>();
				
				var currentParagraph = new StringBuilder();
				for (var s : lines) {
						var line = s.trim();
						
						var isSkipped = Arrays.stream(removedLines).anyMatch(line::equalsIgnoreCase);
						if (isSkipped) continue;
						
						var isHeading = line.startsWith("#");
						var isListing = line.matches("[0-9]\\.\\s.+");
						var isUnorderedList = line.matches("-\\s.+");
						
						if (isHeading || isListing || isUnorderedList) {
								paragraphList.add(currentParagraph.toString());
								currentParagraph = new StringBuilder();
								currentParagraph.append(line);
								continue;
						}
						
						var isSeparator = line.isEmpty();
						if (isSeparator) {
								paragraphList.add(currentParagraph.toString());
								currentParagraph = new StringBuilder();
						} else {
								currentParagraph.append(line);
						}
				}
				
				if (!currentParagraph.isEmpty())
					paragraphList.add(currentParagraph.toString());
				
				return paragraphList;
		}
		
		private static Component parseImageParagraph(String paragraphString, String bookId) {
				
				var doc = Jsoup.parseBodyFragment(paragraphString);
				var element = doc.selectFirst("ModAsset");
				
				if (element != null) {
						var location = element.attr("location");
						var width = element.attr("width");
						
						var imageModId = location.split(":")[0];
						var imageModPath = location.split(":")[1];
						var searchPath = Identifier.of(Oracle.MOD_ID, "books/" + bookId + "/.assets/item/" + bookId + "/" + imageModPath + ".png");
						
						var resource = MinecraftClient.getInstance().getResourceManager().getResource(searchPath);
						if (resource.isEmpty()) {
								return Components.label(Text.literal("Image not found: " + searchPath).formatted(Formatting.RED));
						}
						
						try {
								var image = NativeImage.read(resource.get().getInputStream());
								return Components.texture(searchPath, 0, 0, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());
						} catch (IOException e) {
								return Components.label(Text.literal("Image couldn't be read: " + location + "\n" + e.getMessage().formatted(Formatting.RED)));
						}
						
				} else {
						return Components.label(Text.literal("Image path couldn't be parsed in: " + paragraphString).formatted(Formatting.RED));
				}
		}
		
		private static Component parseParagraphToLabel(String paragraphString, Predicate<String> linkHandler) {
				var paragraphText = Text.empty();
				var index = 0;
				var processedParagraphString = paragraphString;
				
				// process headings
				var headingLevel = getHeadingLevel(paragraphString);
				if (headingLevel > 0) {
						var headingPattern = Pattern.compile("^(#+)\\s*(.+)");
						var headingMatcher = headingPattern.matcher(paragraphString);
						if (headingMatcher.find()) {
								processedParagraphString = headingMatcher.group(2).trim(); // Extract the heading text without '#' and spaces
						}
				}
				
				while (index < processedParagraphString.length()) {
						
						// Check for links: [link text](url)
						var linkMatcher = Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)").matcher(processedParagraphString);
						if (linkMatcher.find(index) && linkMatcher.start() == index) {
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
						if (boldMatcher.find(index) && boldMatcher.start() == index) {
								var boldText = (boldMatcher.group(1) != null) ? boldMatcher.group(1) : boldMatcher.group(2);
								paragraphText.append(Text.literal(boldText).setStyle(Style.EMPTY.withBold(true)));
								index += boldMatcher.group(0).length();
								continue;
						}
						
						// Check for italic text: *text* or _text_
						var italicMatcher = Pattern.compile("\\*([^*]+)\\*|_([^_]+)_").matcher(processedParagraphString);
						if (italicMatcher.find(index) && italicMatcher.start() == index) {
								var italicText = (italicMatcher.group(1) != null) ? italicMatcher.group(1) : italicMatcher.group(2);
								paragraphText.append(Text.literal(italicText).setStyle(Style.EMPTY.withItalic(true)));
								index += italicMatcher.group(0).length();
								continue;
						}
						
						//Check for color (example: #FF0000 text #FFFFFF )
						var colorMatcher = Pattern.compile("#([0-9A-Fa-f]{6})\\s*([^#]+?)\\s*#([0-9A-Fa-f]{6})").matcher(processedParagraphString);
						if (colorMatcher.find(index) && colorMatcher.start() == index) {
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
						paragraphText.formatted(Formatting.BOLD);
				
				var label = new ScalableLabelComponent(paragraphText, linkHandler);
				if (headingLevel > 0) {
						label.scale = 2f - headingLevel * 0.2f;
				}
				label.lineHeight(11);
				return label;
		}
		
		public static int getHeadingLevel(String paragraphString) {
				var headingMatcher = Pattern.compile("^(#+)\\s*(.+)").matcher(paragraphString.trim());
				if (headingMatcher.find()) {
						return headingMatcher.group(1).length();
				}
				return 0; // Not a heading
		}
		
}