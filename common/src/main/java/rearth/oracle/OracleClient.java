package rearth.oracle;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.resources.Identifier;
import com.mojang.datafixers.util.Pair;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import rearth.oracle.docs.DocsFormat;
import rearth.oracle.docs.DocsIndexer;
import rearth.oracle.docs.DocsMode;
import rearth.oracle.progress.AdvancementProgressValidator;
import rearth.oracle.tooltip.DocumentationTooltipHandler;
import rearth.oracle.ui.OracleScreen;
import rearth.oracle.ui.SearchScreen;
import rearth.oracle.util.TitleLookup;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public final class OracleClient {
    
    public static final String ROOT_DIR = "books";   // wikis would be more fitting, but this is kept for compat reasons
    
    private static final KeyMapping.Category ORACLE_CATEGORY =
      new KeyMapping.Category(Identifier.fromNamespaceAndPath(Oracle.MOD_ID, "oracle"));
    public static final KeyMapping ORACLE_WIKI = new KeyMapping("key.oracle_index.open", GLFW.GLFW_KEY_H, ORACLE_CATEGORY);
    public static final KeyMapping ORACLE_SEARCH = new KeyMapping("key.oracle_index.search", -1, ORACLE_CATEGORY);
    
    public static final Map<String, DocsFormat> LOADED_WIKIS = new HashMap<>(); // map of loaded wiki ids to formats (specifies directory layout)
    public static final Map<Identifier, ItemArticleRef> ITEM_LINKS = new HashMap<>();   // items that have a corresponding wiki page (docs or content)
    public static final Map<String, Pair<String, String>> UNLOCK_CRITERIONS = new HashMap<>();  // path/key here is: "books/modid/folder/entry.mdx". Value is unlock type and content
    public static final Map<String, Set<DocsMode>> AVAILABLE_MODES = new HashMap<>(); // wikiID -> Set of available modes (e.g., "oritech" -> ["docs", "content"])
    public static final Map<String, Identifier> CONTENT_ID_MAP = new HashMap<>();// item / block id -> resource path (e.g., "oritech:enderic_laser" -> "oracle_index:books/oritech/.content/machines/laser.mdx")
    public static final Map<String, Map<String, Identifier>> CONTENT_REF_MAP = new HashMap<>();// page ref -> resource path (e.g., "colored_cables" -> "oracle_index:books/oritech/.content/cabling/colored_cables.mdx")
    
    private static SemanticSearch searchInstance;
    
    public static void init() {
        Oracle.LOGGER.info("Hello from the Oracle Wiki Client!");
        
        KeyMappingRegistry.register(ORACLE_WIKI);
        KeyMappingRegistry.register(ORACLE_SEARCH);
        
        Configurator.setLevel("ai.djl.util.Platform", Level.WARN);
        
        AdvancementProgressValidator.register();
        DocumentationTooltipHandler.register();
        
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (ORACLE_WIKI.consumeClick()) {
                
                if (client.hasControlDown()) {
                    Oracle.LOGGER.info("Opening Oracle Search...");
                    client.setScreen(new SearchScreen(client.screen));
                    return;
                }
                
                Oracle.LOGGER.info("Opening Oracle Wiki...");
                client.setScreen(new OracleScreen(client.screen));
            }
            
            if (ORACLE_SEARCH.consumeClick()) {
                Oracle.LOGGER.info("Opening Oracle Search...");
                client.setScreen(new SearchScreen(client.screen));
            }
        });
        
        ReloadListenerRegistry.register(PackType.CLIENT_RESOURCES, (ResourceManagerReloadListener) manager -> {
            Oracle.LOGGER.info("Indexing Oracle Wiki Resources...");
            findAllResourceEntries(manager);
            getOrCreateSearch();    // start search to begin indexing in advance
        }, Identifier.fromNamespaceAndPath(Oracle.MOD_ID, "wiki_resources"));
        
    }
    
    /**
     * Opens the Oracle Screen, potentially setting the active wiki and entry.
     *
     * @param wikiId  The ID of the wiki to activate. If null, the last active wiki remains active.
     * @param entryId The Identifier of the entry to activate. If null, the currently active entry remains active. Example format: {@code oracle_index:books/oritech/interaction/enderic_laser.mdx}
     * @param parent  The parent screen. This is the screen that will be returned to when the wiki is closed. Usually just {@code Minecraft.getInstance().screen} works here.
     * @warning If {@code entryId} is set, {@code wikiId} should generally also be set to ensure the correct wiki is active.
     * Otherwise, the behavior depends on the currently active wiki, and could lead to unexpected results. Only omit
     * {@code wikiId} if you are certain that the correct wiki is already active.
     */
    public static void openScreen(@Nullable String wikiId, @Nullable Identifier entryId, @Nullable Screen parent) {
        if (wikiId != null)
            OracleScreen.activeWiki = wikiId;
        if (entryId != null)
            OracleScreen.activeEntry = entryId;
        
        Minecraft.getInstance().setScreen(new OracleScreen(parent));
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
        
        Minecraft.getInstance().setScreen(new OracleScreen(parent));
        
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
    
    @Nullable
    public static Identifier getPage(String wikiId, String ref) {
        Map<String, Identifier> refs = CONTENT_REF_MAP.get(wikiId);
        return refs != null ? refs.get(ref) : null;
    }

    private static void findAllResourceEntries(ResourceManager manager) {
        TitleLookup.clearCache();

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

            searchInstance = SemanticSearch.create(filter);
        }
        
        return searchInstance;
    }
    
    public static String getActiveLangCode() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }
    
    public static Optional<Identifier> getTranslatedPath(Identifier identifier, String wikiId) {
        
        var languageCode = OracleClient.getActiveLangCode();
        var resourceManager = Minecraft.getInstance().getResourceManager();
        
        if (!languageCode.startsWith("en_")) {
            var format = getWikiFormat(wikiId);
            var translatedDir = format.getTranslatedDir(languageCode);
            var translatedPath = Identifier.fromNamespaceAndPath(identifier.getNamespace(), identifier.getPath().replace(ROOT_DIR + "/" + wikiId, ROOT_DIR + "/" + wikiId + translatedDir));
            
            if (resourceManager.getResource(translatedPath).isPresent()) {
                return Optional.of(translatedPath);
            }
            
        }
        
        return Optional.empty();
    }
    
    public record ItemArticleRef(Identifier linkTarget, Supplier<String> entryName, String wikiId, int pageIDs) {
    }
    
}
