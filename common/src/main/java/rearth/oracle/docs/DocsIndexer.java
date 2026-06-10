package rearth.oracle.docs;

import com.google.common.base.Suppliers;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import rearth.oracle.Oracle;
import rearth.oracle.OracleClient.ItemArticleRef;
import rearth.oracle.util.MarkdownParser;
import rearth.oracle.util.MarkdownParser.Frontmatter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

import static rearth.oracle.OracleClient.ROOT_DIR;

public class DocsIndexer {
    public static final String WIKI_META_FILE = "sinytra-wiki.json";
    public static final String SCHEMA_V1 = "1";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    private final Map<String, DocsFormat> loadedWikis;
    private final Map<Identifier, ItemArticleRef> itemLinks;
    private final Map<String, Pair<String, String>> unlockCriterions;
    private final Map<String, Set<DocsMode>> availableModes;
    private final Map<String, Identifier> contentIds;
    private final Map<String, Identifier> contentRefs;

    public DocsIndexer() {
        this.loadedWikis = new HashMap<>();
        this.itemLinks = new HashMap<>();
        this.unlockCriterions = new HashMap<>();
        this.availableModes = new HashMap<>();
        this.contentIds = new HashMap<>();
        this.contentRefs = new HashMap<>();
    }

    public Map<String, DocsFormat> getLoadedWikis() {
        return loadedWikis;
    }

    public Map<Identifier, ItemArticleRef> getItemLinks() {
        return itemLinks;
    }

    public Map<String, Pair<String, String>> getUnlockCriterions() {
        return unlockCriterions;
    }

    public Map<String, Set<DocsMode>> getAvailableModes() {
        return availableModes;
    }

    public Map<String, Identifier> getContentIds() {
        return contentIds;
    }

    public Map<String, Identifier> getContentRefs() {
        return contentRefs;
    }

    public void findAllResourceEntries(ResourceManager manager) {
        var resources = manager.findResources(ROOT_DIR, path -> path.getPath().endsWith(".mdx"));

        Map<String, List<IdentifiedResource>> wikis = new HashMap<>();
        for (var entry : resources.entrySet()) {
            var resourceId = entry.getKey();
            var path = resourceId.getPath(); // e.g., "books/oritech/.content/machines/laser.mdx"

            var modId = extractModid(path); // e.g., "oritech"
            if (modId == null) continue;

            var modResources = wikis.computeIfAbsent(modId, m -> new ArrayList<>());
            modResources.add(new IdentifiedResource(resourceId, entry.getValue()));
        }

        for (var wiki : wikis.entrySet()) {
            var modId = wiki.getKey();
            var entries = wiki.getValue();
            var format = detectDocsFormat(manager, modId);

            this.loadedWikis.put(modId, format);

            for (IdentifiedResource entry : entries) {
                processEntry(modId, entry.id(), entry.resource(), format);
            }
        }
    }

    private void processEntry(String modId, Identifier resourceId, Resource resource, DocsFormat format) {
        var path = resourceId.getPath(); // e.g., "books/oritech/.content/machines/laser.mdx"

        if (format.isTranslatedPath(path)) {
            return; // skip / don't support translations for now in indexing
        }

        // check docs or content
        var isContent = format.isContentPath(path);
        var mode = isContent ? DocsMode.CONTENT : DocsMode.DOCS;
        this.availableModes.computeIfAbsent(modId, k -> new HashSet<>()).add(mode);

        // parse frontmatter
        try (var inputStream = resource.getInputStream()) {
            var fileContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            var frontmatter = MarkdownParser.parseFrontmatter(fileContent);

            // map game id for content links
            if (isContent) {
                List<String> ids = frontmatter.getAll("id");
                String ref = computePageRef(resourceId, format, frontmatter, ids);
                this.contentRefs.put(ref, resourceId);

                if (frontmatter.containsKey("id")) {
                    for (String id : ids) {
                        this.contentIds.put(id, resourceId);

                        var itemId = Identifier.of(id);

                        Supplier<String> lazyTitle;
                        var title = frontmatter.getOrDefault("title", "missing");
                        if (title.equals("missing") && Registries.ITEM.containsId(itemId)) {
                            // Use supplier as translations may not be available at this time yet
                            lazyTitle = Suppliers.memoize(() -> I18n.translate(Registries.ITEM.get(itemId).getTranslationKey()));
                        } else {
                            lazyTitle = () -> title;
                        }

                        // TODO Pick best page for item
                        this.itemLinks.put(itemId, new ItemArticleRef(resourceId, lazyTitle, modId));
                    }
                }
            }

            // frontmatter custom item links indexing
            if (frontmatter.containsKey("related_items")) {
                var baseString = frontmatter.getOne("related_items").replace("[", "").replace("]", "").replace("\"", "");
                for (var itemString : baseString.split(", ")) {
                    var itemId = Identifier.of(itemString.trim());
                    var title = frontmatter.getOrDefault("title", "missing");
                    this.itemLinks.put(itemId, new ItemArticleRef(resourceId, () -> title, modId));
                }
            }

            if (frontmatter.containsKey("unlock")) {
                var unlockText = frontmatter.getOne("unlock");
                var parts = unlockText.split(":", 2);
                if (parts.length == 2) {
                    this.unlockCriterions.put(path, new Pair<>(parts[0], parts[1]));
                }
            }

        } catch (IOException e) {
            Oracle.LOGGER.error("Unable to load book entry: {}", resourceId, e);
        }
    }

    private static DocsFormat detectDocsFormat(ResourceManager manager, String wikiId) {
        var metaPath = Identifier.of(Oracle.MOD_ID, ROOT_DIR + "/" + wikiId + "/" + WIKI_META_FILE);
        WikiMetaStub meta = manager.getResource(metaPath)
            .map(DocsIndexer::parseWikiMeta)
            .orElse(null);

        if (meta != null && SCHEMA_V1.equals(meta.schema)) {
            return new V1DocsFormat();
        }

        return new LegacyDocsFormat();
    }

    /**
     * Compute page ref using the same process as the wiki web
     */
    @Nullable
    private String computePageRef(Identifier resourceId, DocsFormat format, Frontmatter frontmatter, List<String> ids) {
        // 1. Try using user-specified ref
        var userRef = frontmatter.getOne("ref");
        if (userRef != null && !this.contentRefs.containsKey(userRef)) {
            return userRef;
        }

        // 2. Try deriving the ref from a single specified item ID
        if (ids.size() == 1) {
            var id = Identifier.tryParse(ids.getFirst());
            if (id != null) {
                var primaryRef = id.getPath().replace("/", "_");
                if (!this.contentRefs.containsKey(primaryRef)) {
                    return primaryRef;
                }
            }
        }

        // 3. Try deriving from the page file name
        var relativePath = format.stripContentPrefix(resourceId.getPath());
        var stripped = List.of(relativePath.split("\\.")).getLast();
        var fileName = List.of(stripped.split("/")).getLast();
        var normalized = fileName.replace("/", "_");
        if (!this.contentRefs.containsKey(normalized)) {
            return normalized;
        }

        // 4. Use the full path
        return stripped.replace("/", "_");
    }

    @Nullable
    private static WikiMetaStub parseWikiMeta(Resource resource) {
        try (Reader reader = new InputStreamReader(resource.getInputStream())) {
            return GSON.fromJson(reader, WikiMetaStub.class);
        } catch (Exception e) {
            LOGGER.error("Error parsing wiki metadata", e);
            return null;
        }
    }

    @Nullable
    public static String extractModid(String path) {
        var segments = path.split("/");
        if (segments.length < 2) {
            return null;
        }
        return segments[1];
    }

    record IdentifiedResource(Identifier id, Resource resource) {
    }

    record WikiMetaStub(@Nullable String schema) {
    }
}
