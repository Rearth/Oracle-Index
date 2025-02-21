package rearth.oracle;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import io.wispforest.owo.ui.util.Delta;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import rearth.oracle.ui.OracleScreen;
import rearth.oracle.util.MarkdownParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public final class OracleClient {
    
    public static final KeyBinding ORACLE_WIKI = new KeyBinding("key.oracle_wiki.open", GLFW.GLFW_KEY_H, "key.categories.misc");
    
    public static final Set<String> LOADED_BOOKS = new HashSet<>();
    public static final HashMap<Identifier, BookItemLink> ITEM_LINKS = new HashMap<>();
    
    public static ItemStack tooltipStack;
    public static float openEntryProgress = 0;

    public static void init() {
        Oracle.LOGGER.info("Hello from the Oracle Wiki Client!");
        
        KeyMappingRegistry.register(ORACLE_WIKI);
        
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (ORACLE_WIKI.wasPressed()) {
                Oracle.LOGGER.info("Opening Oracle Wiki...");
                client.setScreen(new OracleScreen());
            }
        });
        
        ClientLifecycleEvent.CLIENT_STARTED.register(client -> {
            Oracle.LOGGER.info("Indexing entry items...");
            findAllResourceEntries();
        });
        
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (Screen.hasAltDown()) return;
            openEntryProgress += Delta.compute(openEntryProgress, 0, 0.13f);
        });
        
    }
    
    private static void findAllResourceEntries() {
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var resources = resourceManager.findResources("books", path -> path.getPath().endsWith(".mdx"));
        
        LOADED_BOOKS.clear();
        
        for (var resourceId : resources.keySet()) {
            var purePath = resourceId.getPath().replaceFirst("books/", "");
            var segments = purePath.split("/");
            var modId = segments[0];        // e.g. "oritech"
            var entryPath = purePath.replaceFirst(modId + "/", ""); // e.g. "tools/wrench.mdx"
            var entryFileName = segments[segments.length - 1]; // e.g. "wrench.mdx"
            var entryDirectory = entryPath.replace(entryFileName, ""); // e.g. "tools" or "processing/reactor"
		        
		        try {
				        var fileContent = new String(resources.get(resourceId).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                var fileComponents = MarkdownParser.parseFrontmatter(fileContent);
                if (fileComponents.containsKey("related_items")) {
                    var baseString = fileComponents.get("related_items").replace("[", "").replace("]", "");
                    var itemStrings = baseString.split(", ");
                    for (var itemString : itemStrings) {
                        var itemId = Identifier.of(itemString);
                        var linkData = new BookItemLink(resourceId, fileComponents.getOrDefault("title", "missing"), modId);
                        ITEM_LINKS.put(itemId, linkData);
                    }
                }
		        } catch (IOException e) {
                Oracle.LOGGER.error("Unable to load book with id: " + resourceId);
				        throw new RuntimeException(e);
		        }
		        
		        LOADED_BOOKS.add(modId);
        }
    }
    
    public record BookItemLink(Identifier linkTarget, String entryName, String bookId) {}
    
}
