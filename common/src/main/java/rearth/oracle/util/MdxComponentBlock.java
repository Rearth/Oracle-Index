package rearth.oracle.util;

import org.commonmark.node.CustomBlock;
import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public abstract class MdxComponentBlock extends CustomBlock {
    
    protected String rawContent;
    
    public void setupContent(String rawContent) {
        this.rawContent = rawContent;
        
        parseContent();
    }
    
    public String getRawContent() { return rawContent; }
    
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
                } catch (NumberFormatException ignored) {}
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
        public String variant = "info";
        
        @Override
        void parseContent() {
            var el = Jsoup.parseBodyFragment(rawContent).selectFirst("Callout");
            if (el != null) {
                if (el.hasAttr("variant")) {
                    this.variant = el.attr("variant");
                }
            }
        }
        
        @Override
        public String toString() {
            return "CalloutBlock{" +
                     "variant='" + variant + '\'' +
                     ", rawContent='" + rawContent + '\'' +
                     '}';
        }
    }
    
    public static class AssetBlock extends MdxComponentBlock {
        private final boolean isModAsset;
        public String location;
        public String width = "50%";
        
        public AssetBlock(boolean isModAsset) { this.isModAsset = isModAsset; }
        public boolean isModAsset() { return isModAsset; }
        
        @Override
        void parseContent() {
            var tagName = isModAsset ? "ModAsset" : "Asset";
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
                     "isModAsset=" + isModAsset +
                     ", location='" + location + '\'' +
                     ", width='" + width + '\'' +
                     ", rawContent='" + rawContent + '\'' +
                     '}';
        }
    }
    
    public static class PrefabObtainingBlock extends MdxComponentBlock {
        // todo
        @Override
        void parseContent() {}
    }
}
