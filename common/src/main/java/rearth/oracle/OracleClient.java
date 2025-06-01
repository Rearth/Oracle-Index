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
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import rearth.oracle.ui.OracleScreen;
import rearth.oracle.ui.SearchScreen;
import rearth.oracle.util.BookMetadata;
import rearth.oracle.util.MarkdownParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class OracleClient {
    
    public static final KeyBinding ORACLE_WIKI = new KeyBinding("key.oracle_index.open", GLFW.GLFW_KEY_H, "key.categories.oracle");
    public static final KeyBinding ORACLE_SEARCH = new KeyBinding("key.oracle_index.search", -1, "key.categories.oracle");
    
    public static final HashMap<String, BookMetadata> LOADED_BOOKS = new HashMap<>();
    public static final HashMap<Identifier, BookItemLink> ITEM_LINKS = new HashMap<>();
    
    public static ItemStack tooltipStack;
    public static float openEntryProgress = 0;
    
    private static SemanticSearch searchInstance;

    public static void init() {
        Oracle.LOGGER.info("Hello from the Oracle Wiki Client!");
        
        KeyMappingRegistry.register(ORACLE_WIKI);
        KeyMappingRegistry.register(ORACLE_SEARCH);
        
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (ORACLE_WIKI.wasPressed()) {
                
                if (Screen.hasControlDown()) {
                    Oracle.LOGGER.info("Opening Oracle Search...");
                    client.setScreen(new SearchScreen(client.currentScreen));
                    return;
                }
                
                Oracle.LOGGER.info("Opening Oracle Wiki...");
                client.setScreen(new OracleScreen(client.currentScreen));
            }
            
            if (ORACLE_SEARCH.wasPressed()) {
                Oracle.LOGGER.info("Opening Oracle Search...");
                client.setScreen(new SearchScreen(client.currentScreen));
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
    
    /**
     * Opens the Oracle Screen, potentially setting the active book and entry.
     *
     * @param bookId  The ID of the book to activate. If null, the last active book remains active.
     * @param entryId The Identifier of the entry to activate. If null, the currently active entry remains active. Example format: {@code oracle_index:books/oritech/interaction/enderic_laser.mdx}
     * @param parent  The parent screen. This is the screen that will be returned to when the wiki is closed. Usually just {@code MinecraftClient.getInstance().currentScreen} works here.
     *
     * @warning If {@code entryId} is set, {@code bookId} should generally also be set to ensure the correct book is active.
     *          Otherwise, the behavior depends on the currently active book, and could lead to unexpected results. Only omit
     *          {@code bookId} if you are certain that the correct book is already active.
     */
    public static void openScreen(@Nullable String bookId, @Nullable Identifier entryId, @Nullable Screen parent) {
        if (bookId != null)
            OracleScreen.activeBook = LOADED_BOOKS.get(bookId);
        if (entryId != null)
            OracleScreen.activeEntry = entryId;
        
        MinecraftClient.getInstance().setScreen(new OracleScreen(parent));
    }
    
    private static void findAllResourceEntries() {
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var resources = resourceManager.findResources("books", path -> path.getPath().endsWith(".mdx"));
        
        LOADED_BOOKS.clear();
        var supportedLanguages = new HashSet<String>();
        
        for (var resourceId : resources.keySet()) {

            var purePath = resourceId.getPath().replaceFirst("books/", "");
            var segments = purePath.split("/");
            var modId = segments[0];        // e.g. "oritech"
            var entryPath = purePath.replaceFirst(modId + "/", ""); // e.g. "tools/wrench.mdx"
            var entryFileName = segments[segments.length - 1]; // e.g. "wrench.mdx"
            var entryDirectory = entryPath.replace(entryFileName, ""); // e.g. "tools" or "processing/reactor"


            if (entryPath.startsWith(".translated")) {
                var segments2 = entryDirectory.split("/");
                if (segments2.length > 1) {
                    supportedLanguages.add(segments2[1]);
                } // e.g. ".translated/zh_cn/tools/wrench.mdx" will return "zh_cn"
            }
		        
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

            LOADED_BOOKS.put(modId, new BookMetadata(modId, supportedLanguages));
        }
    }
    
    public static SemanticSearch getOrCreateSearch() {
        if (searchInstance == null) searchInstance = new SemanticSearch();
        
        return searchInstance;
    }
    
    public record BookItemLink(Identifier linkTarget, String entryName, String bookId) {}
    
}
