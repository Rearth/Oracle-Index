package rearth.oracle.util;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import rearth.oracle.OracleClient;
import rearth.oracle.docs.DocsIndexer;
import rearth.oracle.ui.OracleScreen;
import rearth.oracle.util.MarkdownParser.Frontmatter;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TitleLookup {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<Identifier, String> cachedTitles = new HashMap<>();

    public static String getTitle(Identifier pagePath) {
        Identifier localizedPath = getLocalizedPath(pagePath);
        return cachedTitles.computeIfAbsent(localizedPath, ignored -> computeTitle(localizedPath, pagePath));
    }

    private static String computeTitle(Identifier pagePath, Identifier fallbackPath) {
        String markdown = parseContents(pagePath);
        if (markdown == null) {
            return fallbackTitle(fallbackPath);
        }

        Frontmatter frontMatter = MarkdownParser.parseFrontmatter(markdown);

        String title = frontMatter.getOne("title");
        if (title != null) return title;

        String headingTitle = MarkdownParser.parseHeadingTitle(markdown);
        if (headingTitle != null) {
            return headingTitle;
        }

        String item = frontMatter.getOne("id");
        if (item != null) {
            if (Identifier.tryParse(item) != null && BuiltInRegistries.ITEM.containsKey(Identifier.parse(item))) {
                return I18n.get(BuiltInRegistries.ITEM.getValue(Identifier.parse(item)).getDescriptionId());
            }
            return item;
        }

        return fallbackTitle(fallbackPath);
    }

    private static Identifier getLocalizedPath(Identifier pagePath) {
        String wikiId = DocsIndexer.extractModid(pagePath.getPath());
        if (wikiId == null || !OracleClient.LOADED_WIKIS.containsKey(wikiId)) return pagePath;

        var format = OracleClient.getWikiFormat(wikiId);
        if (format.isTranslatedPath(pagePath.getPath())) return pagePath;
        return OracleClient.getTranslatedPath(pagePath, wikiId).orElse(pagePath);
    }

    private static String fallbackTitle(Identifier pagePath) {
        return OracleScreen.PAGE_FALLBACK_NAMES.getOrDefault(pagePath, I18n.get("oracle_index.page.untitled"));
    }

    public static void clearCache() {
        cachedTitles.clear();
    }

    @Nullable
    private static String parseContents(Identifier pagePath) {
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            var rc = rm.getResource(pagePath);
            if (rc.isEmpty()) {
                return null;
            }
            return new String(rc.get().open().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Error parsing markdown title for page {}", pagePath, e);
            return null;
        }
    }
}
