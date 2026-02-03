package rearth.oracle;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import io.wispforest.owo.ui.util.Delta;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
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

/* steps for content wiki:

- Option to also display content in navigation bar
- Init check to see which wiki modes are available
- Button to toggle current mode on navigation (if both available)
- Add button to go to last page. Also add button to close wiki screen.
- Handle [linkname](@modid:item) to resolve content links. Create id caches in init()
- Handle [linkname](@item) to resolve to minecraft wiki links
- Handle [linkname]($path/to/page) to resolve to docu links.
- Handle ![](@asset) to handle images (either ingame images or from .assets)
- Add infobox to content entries, with grid on top of page. Collect properties from ingame registries only.
- PrefabObtaining is skipped / ignored for now.

*/
public final class OracleClient {
    
    public static final String ROOT_DIR = "books";   // wikis would be more fitting, but this is kept for compat reasons
    
    public static final KeyBinding ORACLE_WIKI = new KeyBinding("key.oracle_index.open", GLFW.GLFW_KEY_H, "key.categories.oracle");
    public static final KeyBinding ORACLE_SEARCH = new KeyBinding("key.oracle_index.search", -1, "key.categories.oracle");
    
    public static final Set<String> LOADED_WIKIS = new HashSet<>(); // just keeps a set of loaded wiki ids
    public static final HashMap<Identifier, ItemArticleRef> ITEM_LINKS = new HashMap<>();   // items that have a corresponding wiki page (docs or content)
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
        
        ReloadListenerRegistry.register(ResourceType.CLIENT_RESOURCES, (SynchronousResourceReloader) manager -> {
            Oracle.LOGGER.info("Indexing Oracle Wiki Resources...");
            findAllResourceEntries(manager);
        });
        
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (Screen.hasAltDown()) return;
            openEntryProgress += Delta.compute(openEntryProgress, 0, 0.13f);
        });
        
    }
    
    /**
     * Opens the Oracle Screen, potentially setting the active wiki and entry.
     *
     * @param wikiId  The ID of the wiki to activate. If null, the last active wiki remains active.
     * @param entryId The Identifier of the entry to activate. If null, the currently active entry remains active. Example format: {@code oracle_index:books/oritech/interaction/enderic_laser.mdx}
     * @param parent  The parent screen. This is the screen that will be returned to when the wiki is closed. Usually just {@code MinecraftClient.getInstance().currentScreen} works here.
     * @warning If {@code entryId} is set, {@code wikiId} should generally also be set to ensure the correct wiki is active.
     * Otherwise, the behavior depends on the currently active wiki, and could lead to unexpected results. Only omit
     * {@code wikiId} if you are certain that the correct wiki is already active.
     */
    public static void openScreen(@Nullable String wikiId, @Nullable Identifier entryId, @Nullable Screen parent) {
        if (wikiId != null)
            OracleScreen.activeWiki = wikiId;
        if (entryId != null)
            OracleScreen.activeEntry = entryId;
        
        MinecraftClient.getInstance().setScreen(new OracleScreen(parent));
    }
    
    private static void findAllResourceEntries(ResourceManager manager) {
        var resources = manager.findResources(ROOT_DIR, path -> path.getPath().endsWith(".mdx"));
        
        LOADED_WIKIS.clear();
        ITEM_LINKS.clear();
        UNLOCK_CRITERIONS.clear();
        
        for (var resourceId : resources.keySet()) {
            var purePath = resourceId.getPath().replaceFirst(ROOT_DIR + "/", "");
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
                        var linkData = new ItemArticleRef(resourceId, fileComponents.getOrDefault("title", "missing"), modId);
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
                Oracle.LOGGER.error("Unable to load wiki with id: " + resourceId);
                throw new RuntimeException(e);
            }
            
            LOADED_WIKIS.add(modId);
        }
    }
    
    public static SemanticSearch getOrCreateSearch() {
        
        if (searchInstance == null) searchInstance = new SemanticSearch();
        
        return searchInstance;
    }
    
    public static String getActiveLangCode() {
        return MinecraftClient.getInstance().getLanguageManager().getLanguage();
    }
    
    public static Optional<Identifier> getTranslatedPath(Identifier identifier, String wikiId) {
        
        var languageCode = OracleClient.getActiveLangCode();
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        
        if (!languageCode.startsWith("en_")) {
            var translatedPath = Identifier.of(identifier.getNamespace(), identifier.getPath().replace(ROOT_DIR + "/" + wikiId, ROOT_DIR + "/" + wikiId + "/.translated/" + languageCode));
            
            if (resourceManager.getResource(translatedPath).isPresent()) {
                return Optional.of(translatedPath);
            }
            
        }
        
        return Optional.empty();
    }
    
    public record ItemArticleRef(Identifier linkTarget, String entryName, String wikiId) {
    }
    
}
