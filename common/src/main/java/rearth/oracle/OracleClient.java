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
import net.minecraft.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import rearth.oracle.progress.AdvancementProgressValidator;
import rearth.oracle.ui.OracleScreen;
import rearth.oracle.ui.SearchScreen;
import rearth.oracle.util.MarkdownParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class OracleClient {
    
    public static final KeyBinding ORACLE_WIKI = new KeyBinding("key.oracle_index.open", GLFW.GLFW_KEY_H, "key.categories.oracle");
    public static final KeyBinding ORACLE_SEARCH = new KeyBinding("key.oracle_index.search", -1, "key.categories.oracle");
    
    public static final Set<String> LOADED_BOOKS = new HashSet<>();
    public static final HashMap<Identifier, BookItemLink> ITEM_LINKS = new HashMap<>();
    public static final HashMap<String, Pair<String, String>> UNLOCK_CRITERIONS = new HashMap<>();  // path/key here is: "books/modid/folder/entry.mdx". Value is unlock type and content
    
    public static ItemStack tooltipStack;
    public static float openEntryProgress = 0;
    
    private static SemanticSearch searchInstance;
    
    public static void init() {
        Oracle.LOGGER.info("Hello from the Oracle Wiki Client!");
        
        KeyMappingRegistry.register(ORACLE_WIKI);
        KeyMappingRegistry.register(ORACLE_SEARCH);
        
        AdvancementProgressValidator.register();
        
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
     * @warning If {@code entryId} is set, {@code bookId} should generally also be set to ensure the correct book is active.
     * Otherwise, the behavior depends on the currently active book, and could lead to unexpected results. Only omit
     * {@code bookId} if you are certain that the correct book is already active.
     */
    public static void openScreen(@Nullable String bookId, @Nullable Identifier entryId, @Nullable Screen parent) {
        if (bookId != null)
            OracleScreen.activeBook = bookId;
        if (entryId != null)
            OracleScreen.activeEntry = entryId;
        
        MinecraftClient.getInstance().setScreen(new OracleScreen(parent));
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
            
            if (entryDirectory.startsWith(".translated")) continue; // skip / don't support translations for now
            
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
                if (fileComponents.containsKey("unlock")) {
                    var unlockText = fileComponents.get("unlock");
                    var unlockParts = unlockText.split(":", 2);
                    if (unlockParts.length == 2) {
                        var unlockType = unlockParts[0];
                        var unlockContent = unlockParts[1];
                        UNLOCK_CRITERIONS.put(resourceId.getPath(), new Pair<>(unlockType, unlockContent));
                    }
                }
                
            } catch (IOException e) {
                Oracle.LOGGER.error("Unable to load book with id: " + resourceId);
                throw new RuntimeException(e);
            }
            
            LOADED_BOOKS.add(modId);
        }
    }
    
    public static SemanticSearch getOrCreateSearch() {
        
        if (searchInstance == null) searchInstance = new SemanticSearch();
        
        return searchInstance;
    }
    
    public static String getActiveLangCode() {
        return MinecraftClient.getInstance().getLanguageManager().getLanguage();
    }
    
    public static Optional<Identifier> getTranslatedPath(Identifier identifier, String bookId) {
        
        var languageCode = OracleClient.getActiveLangCode();
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        
        if (!languageCode.startsWith("en_")) {
            var translatedPath = Identifier.of(identifier.getNamespace(), identifier.getPath().replace("books/" + bookId, "books/" + bookId + "/.translated/" + languageCode));
            
            if (resourceManager.getResource(translatedPath).isPresent()) {
                return Optional.of(translatedPath);
            }
            
        }
        
        return Optional.empty();
    }
    
    public record BookItemLink(Identifier linkTarget, String entryName, String bookId) {
    }
    
}
