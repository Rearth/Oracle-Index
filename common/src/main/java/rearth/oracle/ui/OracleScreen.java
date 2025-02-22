package rearth.oracle.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextureComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import rearth.oracle.Oracle;
import rearth.oracle.OracleClient;
import rearth.oracle.ui.components.ColoredCollapsibleContainer;
import rearth.oracle.ui.components.ScalableLabelComponent;
import rearth.oracle.util.MarkdownParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class OracleScreen extends BaseOwoScreen<FlowLayout> {
		
		private FlowLayout navigationBar;
		private FlowLayout contentContainer;
		private FlowLayout rootComponent;
		private FlowLayout leftPanel;
		private ScrollContainer<FlowLayout> outerContentContainer;
		
		private boolean needsLayout = false;
		
		public static Identifier activeEntry;
		public static String activeBook;
		
		private static final int wideContentWidth = 50; // in %
		
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
				
				outerContentContainer = Containers.verticalScroll(Sizing.fill(wideContentWidth), Sizing.fill(), contentContainer);
				rootComponent.child(outerContentContainer);
				
				buildModNavigation(leftPanel);
				
				var outerNavigationBarContainer = Containers.verticalScroll(Sizing.content(3), Sizing.fill(), navigationBar);
				leftPanel.child(outerNavigationBarContainer);
				
		}
		
		@Override
		protected void init() {
				super.init();
				
				updateLayout();
		}
		
		private void updateLayout() {
				var leftOffset = Math.max(15, this.width / 17);
				
				// "responsive" layout
				if (this.width >= 650) {
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
						var leftPanelSize = leftPanel.width();
						outerContentContainer.horizontalSizing(Sizing.fixed(this.width - leftPanelSize - 15));
				}
		}
		
		@Override
		public void tick() {
				super.tick();
				
				if (needsLayout) {
						needsLayout = false;
						this.updateLayout();
				}
				
		}
		
		private void loadContentContainer(Identifier filePath, String bookId) throws IOException {
				
				contentContainer.clearChildren();
				activeEntry = filePath;
				
				var resourceManager = MinecraftClient.getInstance().getResourceManager();
				var resourceCandidate = resourceManager.getResource(filePath);
				
				if (resourceCandidate.isEmpty()) {
						System.out.println("No content file found for " + filePath);
						return;
				}
				
				var fileContent = new String(resourceCandidate.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				var parsedTexts = MarkdownParser.parseMarkdownToOwoComponents(fileContent, bookId, link -> {
						
						if (link.startsWith("http")) return false;
						
						var pathSegments = filePath.getPath().split("/");
						var newPath = "";
						
						// build path based on relative information
						var parentIteration = link.startsWith("../") ? 1 : 0;
						for (int i = 0; i < pathSegments.length - 1 - parentIteration; i++) {
								newPath += pathSegments[i] + "/";
						}
						
						newPath = newPath.split("#")[0];    // anchors are not supported, so we just remove them
						newPath += link.replace("../", "") + ".mdx";    // add file ending
						
						var newId = Identifier.of(Oracle.MOD_ID, newPath);
						
						try {
								loadContentContainer(newId, bookId);
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
				buttonContainer.child(bookTitleWrapper.zIndex(3));
				
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
		
		private void buildNavigationEntriesForModPath(String bookId, String path, FlowLayout container) {
				
				var resourceManager = MinecraftClient.getInstance().getResourceManager();
				var metaPath = Identifier.of(Oracle.MOD_ID, "books/" + bookId + path + "/_meta.json");
				var resourceCandidate = resourceManager.getResource(metaPath);
				
				if (resourceCandidate.isEmpty()) {
						System.out.println("No _meta.json found for " + bookId + " at " + metaPath);
						return;
				}
				
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
										buildNavigationEntriesForModPath(bookId, path + "/" + entry.id(), directoryContainer);
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
										final var labelPath = Identifier.of(Oracle.MOD_ID, "books/" + bookId + path + "/" + entry.id());
										final var labelText = Text.translatable(entry.name).formatted(Formatting.WHITE);
										final var label = Components.label(labelText.formatted(Formatting.UNDERLINE));
										
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
