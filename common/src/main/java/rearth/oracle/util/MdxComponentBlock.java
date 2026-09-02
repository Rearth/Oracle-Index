package rearth.oracle.util;

import com.mojang.logging.LogUtils;
import org.commonmark.node.CustomBlock;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public abstract class MdxComponentBlock extends CustomBlock {
    private static final Logger LOGGER = LogUtils.getLogger();

    protected String rawContent;

    public void setupContent(String rawContent) {
        this.rawContent = rawContent;

        parseContent();
    }

    public String getRawContent() {
        return rawContent;
    }

    abstract void parseContent();

    public static class CraftingRecipeBlock extends MdxComponentBlock {
        public List<String> slots = new ArrayList<>();
        public String result;
        public int count = 1;

        @Override
        void parseContent() {
            // regex is safer for the MDX array syntax than jsoup: slots={[ ... ]}
            // matches content inside slots={[ ... ]}
            var slotPattern = Pattern.compile("slots=\\{\\[(.*?)]}");
            var slotMatcher = slotPattern.matcher(rawContent.replace("\n", " "));

            if (slotMatcher.find()) {
                var arrayContent = slotMatcher.group(1);
                var items = arrayContent.split(",");
                for (var item : items) {
                    // clean quotes and whitespace: 'mod:item' -> mod:item
                    slots.add(item.trim().replace("'", "").replace("\"", ""));
                }
            }

            // use jsoup for simple attributes like result="mod:item"
            // strip the slots part to not confuse Jsoup
            var safeHtml = rawContent.replaceAll("slots=\\{\\[.*?]}", "");
            var el = Jsoup.parseBodyFragment(safeHtml).selectFirst("CraftingRecipe");

            if (el != null) {
                this.result = el.attr("result");
                var countStr = el.attr("count").replaceAll("[{}]", "");
                try {
                    this.count = Integer.parseInt(countStr);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        @Override
        public String toString() {
            return "CraftingRecipeBlock{" +
                "slots=" + slots +
                ", result='" + result + '\'' +
                ", count=" + count +
                ", rawContent='" + rawContent + '\'' +
                '}';
        }
    }

    public static class CalloutBlock extends MdxComponentBlock {
        public CalloutVariant variant = CalloutVariant.NOTE;
        @Nullable
        public String title;
        public boolean collapsible;
        public boolean collapsed;

        @Override
        void parseContent() {
            var el = element("Callout");
            if (el == null) return;

            var type = el.hasAttr("type") ? el.attr("type") : el.attr("variant");
            this.variant = CalloutVariant.byName(type, CalloutVariant.NOTE);
            if (el.hasAttr("title")) {
                this.title = el.attr("title");
            }
            this.collapsed = el.hasAttr("collapsed");
            this.collapsible = this.collapsed || el.hasAttr("collapsible");
        }

        @Override
        public String toString() {
            return "CalloutBlock{" +
                "variant='" + variant + '\'' +
                ", title='" + title + '\'' +
                ", collapsible=" + collapsible +
                ", collapsed=" + collapsed +
                ", rawContent='" + rawContent + '\'' +
                '}';
        }
    }

    public static class AssetBlock extends MdxComponentBlock {
        private final String tagName;
        public String location;
        public String width = "50%";

        public AssetBlock(String tagName) {
            this.tagName = tagName;
        }

        @Override
        void parseContent() {
            var el = Jsoup.parseBodyFragment(rawContent).selectFirst(tagName);
            if (el != null) {
                this.location = el.attr("location");
                if (el.hasAttr("width")) {
                    this.width = el.attr("width").replaceAll("[{}]", ""); // Clean {50%} to 50%
                }
            }
        }

        @Override
        public String toString() {
            return "AssetBlock{" +
                "location='" + location + '\'' +
                ", width='" + width + '\'' +
                ", rawContent='" + rawContent + '\'' +
                '}';
        }
    }

    public static class CodeTabsBlock extends MdxComponentBlock {
        @Override
        void parseContent() {
        }

        @Override
        public String toString() {
            return "CodeTabsBlock{}";
        }
    }

    @Nullable
    protected Element element(String tagName) {
        return Jsoup.parseBodyFragment(rawContent).selectFirst(tagName);
    }
}
