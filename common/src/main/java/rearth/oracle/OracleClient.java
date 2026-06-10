package rearth.oracle;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import rearth.oracle.docs.DocsFormat;
import rearth.oracle.docs.DocsIndexer;
import rearth.oracle.docs.DocsMode;
import rearth.oracle.progress.AdvancementProgressValidator;
import rearth.oracle.ui.OracleScreen;
import rearth.oracle.ui.SearchScreen;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public final class OracleClient {
    
    public static final String ROOT_DIR = "books";   // wikis would be more fitting, but this is kept for compat reasons
    
    public static final KeyBinding ORACLE_WIKI = new KeyBinding("key.oracle_index.open", GLFW.GLFW_KEY_H, "key.categories.oracle");
    public static final KeyBinding ORACLE_SEARCH = new KeyBinding("key.oracle_index.search", -1, "key.categories.oracle");
    
    public static final Map<String, DocsFormat> LOADED_WIKIS = new HashMap<>(); // map of loaded wiki ids to formats (specifies directory layout)
    public static final HashMap<Identifier, ItemArticleRef> ITEM_LINKS = new HashMap<>();   // items that have a corresponding wiki page (docs or content)
    public static final HashMap<String, Pair<String, String>> UNLOCK_CRITERIONS = new HashMap<>();  // path/key here is: "books/modid/folder/entry.mdx". Value is unlock type and content
    public static final HashMap<String, Set<DocsMode>> AVAILABLE_MODES = new HashMap<>(); // wikiID -> Set of available modes (e.g., "oritech" -> ["docs", "content"])
    public static final HashMap<String, Identifier> CONTENT_ID_MAP = new HashMap<>();// item / block id -> resource path (e.g., "oritech:enderic_laser" -> "oracle_index:books/oritech/.content/machines/laser.mdx")
    public static final HashMap<String, Identifier> CONTENT_REF_MAP = new HashMap<>();// page ref -> resource path (e.g., "colored_cables" -> "oracle_index:books/oritech/.content/cabling/colored_cables.mdx")
    
    public static ItemStack tooltipStack;
    public static float openEntryProgress = 0;
    
    private static SemanticSearch searchInstance;
    
    public static void init() {
        Oracle.LOGGER.info("Hello from the Oracle Wiki Client!");
        
        KeyMappingRegistry.register(ORACLE_WIKI);
        KeyMappingRegistry.register(ORACLE_SEARCH);
        
        Configurator.setLevel("ai.djl.util.Platform", Level.WARN);
        
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
            getOrCreateSearch();    // start search to begin indexing in advance
        });
        
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (Screen.hasAltDown()) return;
            openEntryProgress += (0f - openEntryProgress) * 0.13f;
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
    
    /**
     * Opens an entry in the Oracle Wiki based on the provided asset/content ID.
     *
     * @param assetId The * @param assetId The in-game item ID (e.g., `oritech:nickel_ingot`) used to locate the corresponding content entry.used to locate the corresponding content entry.
     * @param parent  The parent screen to return to when the Oracle Wiki screen is closed. Can be null.
     * @return True if the entry was successfully opened, false otherwise.
     */
    public static boolean openEntry(Identifier assetId, @Nullable Screen parent) {
        var contentKey = assetId.toString();
        if (!CONTENT_ID_MAP.containsKey(contentKey)) return false;
        
        var contentPath = CONTENT_ID_MAP.get(contentKey);
        
        var path = contentPath.getPath(); // e.g., "books/oritech/.content/machines/laser_arm_block.mdx"
        // extract mod id
        var segments = path.split("/");
        if (segments.length < 2) return false;
        
        // e.g., "oritech"
        OracleScreen.activeWiki = segments[1];
        OracleScreen.activeEntry = contentPath;
        
        MinecraftClient.getInstance().setScreen(new OracleScreen(parent));
        
        return true;
    }
    
    
    /**
     * Checks if there is a content entry associated with the given asset ID.
     *
     * @param assetId The in-game item ID (e.g., "oritech:nickel_ingot") used to locate the corresponding content entry.
     * @return True if a content entry exists for the given asset ID, false otherwise.
     */
    public static boolean hasContentEntry(Identifier assetId) {
        return CONTENT_ID_MAP.containsKey(assetId.toString());
    }
    
    public static DocsMode getDocsModeForPage(Identifier pageId) {
        String path = pageId.getPath();
        String modId = Objects.requireNonNull(DocsIndexer.extractModid(path), "modid must be extracted");
        DocsFormat format = getWikiFormat(modId);
        return format.isContentPath(path) ? DocsMode.CONTENT : DocsMode.DOCS;
    }

    public static DocsFormat getWikiFormat(String wikiId) {
        return Objects.requireNonNull(LOADED_WIKIS.get(wikiId), "unknown wiki id");
    }

    private static void findAllResourceEntries(ResourceManager manager) {
        DocsIndexer indexer = new DocsIndexer();
        indexer.findAllResourceEntries(manager);

        LOADED_WIKIS.clear();
        LOADED_WIKIS.putAll(indexer.getLoadedWikis());

        ITEM_LINKS.clear();
        ITEM_LINKS.putAll(indexer.getItemLinks());

        UNLOCK_CRITERIONS.clear();
        UNLOCK_CRITERIONS.putAll(indexer.getUnlockCriterions());

        CONTENT_ID_MAP.clear();
        CONTENT_ID_MAP.putAll(indexer.getContentIds());
        
        CONTENT_REF_MAP.clear();
        CONTENT_REF_MAP.putAll(indexer.getContentRefs());

        AVAILABLE_MODES.clear();
        AVAILABLE_MODES.putAll(indexer.getAvailableModes());
    }
    
    public static SemanticSearch getOrCreateSearch() {
        
        if (searchInstance == null) {
            BiPredicate<String, String> filter = (modId, path) -> {
              DocsFormat format = getWikiFormat(modId);
              return !format.isTranslatedPath(path);
            };

            searchInstance = new SemanticSearch(filter);
        }
        
        return searchInstance;
    }
    
    public static String getActiveLangCode() {
        return MinecraftClient.getInstance().getLanguageManager().getLanguage();
    }
    
    public static Optional<Identifier> getTranslatedPath(Identifier identifier, String wikiId) {
        
        var languageCode = OracleClient.getActiveLangCode();
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        
        if (!languageCode.startsWith("en_")) {
            var format = getWikiFormat(wikiId);
            var translatedDir = format.getTranslatedDir(languageCode);
            var translatedPath = Identifier.of(identifier.getNamespace(), identifier.getPath().replace(ROOT_DIR + "/" + wikiId, ROOT_DIR + "/" + wikiId + translatedDir));
            
            if (resourceManager.getResource(translatedPath).isPresent()) {
                return Optional.of(translatedPath);
            }
            
        }
        
        return Optional.empty();
    }
    
    public record ItemArticleRef(Identifier linkTarget, Supplier<String> entryName, String wikiId) {
    }
    
}
