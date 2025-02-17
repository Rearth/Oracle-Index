package rearth.oracle.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextureComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import rearth.oracle.Oracle;
import rearth.oracle.OracleClient;
import rearth.oracle.util.MarkdownParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class OracleScreen extends BaseOwoScreen<FlowLayout> {
		
		private FlowLayout navigationBar;
		private FlowLayout contentContainer;
		
		@Override
		protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
				return OwoUIAdapter.create(this, Containers::horizontalFlow);
		}
		
		@Override
		protected void build(FlowLayout rootComponent) {
				rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
				
				navigationBar = Containers.verticalFlow(Sizing.content(3), Sizing.content(3));
				rootComponent.child(navigationBar);
				
				contentContainer = Containers.verticalFlow(Sizing.fill(), Sizing.content(3));
				contentContainer.horizontalSizing(Sizing.fill());
				contentContainer.horizontalAlignment(HorizontalAlignment.CENTER);
				
				var container = Containers.verticalScroll(Sizing.fill(60), Sizing.fill(80), contentContainer);
				
				rootComponent.child(container);
				rootComponent.horizontalAlignment(HorizontalAlignment.CENTER);
				rootComponent.verticalAlignment(VerticalAlignment.CENTER);
				
				buildModNavigation(rootComponent);
				
		}
		
		private void loadContentContainer(Identifier filePath, String bookId) throws IOException {
				
				System.out.println("Loading content for " + filePath);
				contentContainer.clearChildren();
				
				var resourceManager = MinecraftClient.getInstance().getResourceManager();
				var resourceCandidate = resourceManager.getResource(filePath);
				
				if (resourceCandidate.isEmpty()) {
						System.out.println("No content file found for " + filePath);
						return;
				}
				
				var fileContent = new String(resourceCandidate.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				var parsedTexts = MarkdownParser.parseMarkdownToOwoComponents(fileContent, bookId, link -> {
						if (!link.startsWith("..")) return false;
						var pathSegments = filePath.getPath().split("/");
						var newPath = "";
						for (int i = 0; i < pathSegments.length - 1 - 1; i++) {
								newPath += pathSegments[i] + "/";
						}
						newPath += link.replace("../", "") + ".mdx";
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
								var maxWidth = this.width * 0.6f;
								var usedWidth = Math.min(maxWidth, textureComponent.visibleArea().get().width() * 0.5f);
								var height = usedWidth / ratio;
								
								System.out.println(ratio + " " + usedWidth + " " + height);
								textureComponent.sizing(Sizing.fixed((int) usedWidth), Sizing.fixed((int) height));
						}
						
						paragraph.margins(Insets.of(4, 1, 0, 0));
						contentContainer.child(paragraph);
				}
		}
		
		private void buildModNavigation(FlowLayout rootComponent) {
				
				// collect all book ids
				var bookIds = OracleClient.RESOURCE_ENTRIES.stream()
					              .map(OracleClient.ResourceEntry::bookId)
					              .distinct()
					              .sorted()
					              .toList();
				
				var modSelectorDropdown = Components.dropdown(Sizing.content(3));
				for (var bookId : bookIds) {
						modSelectorDropdown.button(Text.translatable(bookId), elem -> {
								buildModNavigationBar(bookId);
						});
				}
				
				rootComponent.child(modSelectorDropdown.positioning(Positioning.absolute(10, 10)));
				
				buildModNavigationBar(bookIds.getFirst());
				
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
						for (var entry : entries) {
								if (entry.directory) {
										var directoryContainer = Containers.collapsible(Sizing.content(1), Sizing.content(1), Text.translatable(entry.name()), false);
										buildNavigationEntriesForModPath(bookId, path + "/" + entry.id(), directoryContainer);
										directoryContainer.margins(Insets.of(0, 0, 8, 0));
										container.child(directoryContainer);
								} else {
										final var labelPath = Identifier.of(Oracle.MOD_ID, "books/" + bookId + path + "/" + entry.id());
										final var labelText = Text.translatable(entry.name);
										final var label = Components.label(labelText.formatted(Formatting.UNDERLINE));
										
										label.mouseEnter().subscribe(() -> {
												label.color(new Color(0.9f, 0.9f, 0.95f));
										});
										label.mouseLeave().subscribe(() -> {
												label.color(Color.WHITE);
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
										
										label.margins(Insets.of(3, 2, 1, 2));
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
