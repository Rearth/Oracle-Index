package rearth.oracle;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import io.wispforest.owo.ui.util.Delta;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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

- Add button to go to last page. Also add button to close wiki screen. Maybe near / added to the search button?

*/
public final class OracleClient {
    
    public static final String ROOT_DIR = "books";   // wikis would be more fitting, but this is kept for compat reasons
    
    public static final KeyBinding ORACLE_WIKI = new KeyBinding("key.oracle_index.open", GLFW.GLFW_KEY_H, "key.categories.oracle");
    public static final KeyBinding ORACLE_SEARCH = new KeyBinding("key.oracle_index.search", -1, "key.categories.oracle");
    
    public static final Set<String> LOADED_WIKIS = new HashSet<>(); // just keeps a set of loaded wiki ids
    public static final HashMap<Identifier, ItemArticleRef> ITEM_LINKS = new HashMap<>();   // items that have a corresponding wiki page (docs or content)
    public static final HashMap<String, Pair<String, String>> UNLOCK_CRITERIONS = new HashMap<>();  // path/key here is: "books/modid/folder/entry.mdx". Value is unlock type and content
    public static final HashMap<String, Set<String>> AVAILABLE_MODES = new HashMap<>(); // wikiID -> Set of available modes (e.g., "oritech" -> ["docs", "content"])
    public static final HashMap<String, Identifier> CONTENT_ID_MAP = new HashMap<>();// item / block id -> resource path (e.g., "oritech:enderic_laser" -> "oracle_index:books/oritech/.content/machines/laser.mdx")
    
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
        CONTENT_ID_MAP.clear();
        AVAILABLE_MODES.clear();
        
        for (var entry : resources.entrySet()) {
            var resourceId = entry.getKey();
            var path = resourceId.getPath(); // e.g., "books/oritech/.content/machines/laser.mdx"
            
            // extract mode + mod id
            var segments = path.split("/");
            if (segments.length < 2) continue;
            
            var modId = segments[1]; // e.g., "oritech"
            LOADED_WIKIS.add(modId);
            
            // check docs or content
            var isContent = path.contains("/.content/");
            var mode = isContent ? "content" : "docs";
            AVAILABLE_MODES.computeIfAbsent(modId, k -> new HashSet<>()).add(mode);
            
            // parse frontmatter
            try (var inputStream = entry.getValue().getInputStream()) {
                var fileContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                var frontmatter = MarkdownParser.parseFrontmatter(fileContent);
                
                // map game id for content links
                if (isContent && frontmatter.containsKey("id")) {
                    var id = frontmatter.get("id").trim();
                    CONTENT_ID_MAP.put(id, resourceId);
                    var itemId = Identifier.of(id);
                    var title = frontmatter.getOrDefault("title", "missing");
                    if (title.equals("missing") && Registries.ITEM.containsId(itemId))
                        title = I18n.translate(Registries.ITEM.get(itemId).getTranslationKey());
                    ITEM_LINKS.put(itemId, new ItemArticleRef(resourceId, title, modId));
                }
                
                // frontmatter custom item links indexing
                if (frontmatter.containsKey("related_items")) {
                    var baseString = frontmatter.get("related_items").replace("[", "").replace("]", "");
                    for (var itemString : baseString.split(", ")) {
                        var itemId = Identifier.of(itemString.trim());
                        ITEM_LINKS.put(itemId, new ItemArticleRef(resourceId, frontmatter.getOrDefault("title", "missing"), modId));
                    }
                }
                
                if (frontmatter.containsKey("unlock")) {
                    var unlockText = frontmatter.get("unlock");
                    var parts = unlockText.split(":", 2);
                    if (parts.length == 2) {
                        UNLOCK_CRITERIONS.put(path, new Pair<>(parts[0], parts[1]));
                    }
                }
                
            } catch (IOException e) {
                Oracle.LOGGER.error("Unable to load book entry: {}", resourceId, e);
            }
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
