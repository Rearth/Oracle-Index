package rearth.oracle.test;

import org.commonmark.ext.front.matter.YamlFrontMatterBlock;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rearth.oracle.util.MarkdownParser;
import rearth.oracle.util.MdxBlockFactory;
import rearth.oracle.util.MdxComponentBlock;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownTests {
    
    // Re-create the parser logic here so we don't trigger the static UI initializers in MarkdownParser
    private final Parser parser = Parser.builder()
                                    .extensions(List.of(YamlFrontMatterExtension.create()))
                                    .enabledBlockTypes(Set.of(
                                      Heading.class,
                                      HtmlBlock.class,
                                      ThematicBreak.class,
                                      FencedCodeBlock.class,
                                      BlockQuote.class,
                                      ListBlock.class
                                    ))
                                    .customBlockParserFactory(new MdxBlockFactory())
                                    .build();
    
    
    @Test
    @DisplayName("Frontmatter: Extraction logic")
    void testFrontMatter() {
        String md = "---\ntitle: Nickel\n---\nBody";
        Map<String, String> data = MarkdownParser.parseFrontmatter(md);
        assertEquals("Nickel", data.get("title"));
    }
    
    @Test
    @DisplayName("MDX: Parse Crafting Recipe attributes")
    void testRecipeLogic() {
        String md = """
            <CraftingRecipe
                slots={['iron', 'gold']}
                result="diamond"
                count={2}
            />
            """;
        
        Node doc = parser.parse(md);
        Node firstChild = doc.getFirstChild();
        
        assertTrue(firstChild instanceof MdxComponentBlock.CraftingRecipeBlock);
        MdxComponentBlock.CraftingRecipeBlock recipe = (MdxComponentBlock.CraftingRecipeBlock) firstChild;
        
        assertEquals("diamond", recipe.result);
        assertEquals(2, recipe.count);
        assertTrue(recipe.slots.contains("iron"));
    }
    
    @Test
    @DisplayName("MDX: Parse Callout variants")
    void testCalloutLogic() {
        String md = """
            <Callout variant="warning">
            Content
            </Callout>
            """;
        
        Node doc = parser.parse(md);
        Node firstChild = doc.getFirstChild();
        
        assertTrue(firstChild instanceof MdxComponentBlock.CalloutBlock);
        MdxComponentBlock.CalloutBlock callout = (MdxComponentBlock.CalloutBlock) firstChild;
        assertEquals("warning", callout.variant);
    }
    
    @Test
    @DisplayName("Utility: Image width conversion")
    void testWidthLogic() {
        // Since this is a static pure-math method, we can test it directly!
        assertEquals(0.5f, MarkdownParser.convertImageWidth("50%"));
        assertEquals(0.256f, MarkdownParser.convertImageWidth("{256}"));
    }
    
    @Test
    @DisplayName("Should parse a complex MDX document into the correct AST nodes")
    void testFullDocumentStructure() {
        String fullDoc = """
            ---
            title: Deepslate Nickel
            icon: oritech:nickel_ore
            ---
            
            # Nickel Mining
            Nickel is found in **deepslate** layers.
            
            <Callout variant="info">
            It is best harvested with an Oritech Drill.
            </Callout>
            
            ## Crafting
            <CraftingRecipe
                slots={['a','b','c','d','e','f','g','h','i']}
                result="oritech:nickel_block"
                count={1}
            />
            
            * Use it for plating
            * Use it for magnets
            """;
        
        Node document = parser.parse(fullDoc);
        
        // 1. Verify the tree isn't empty
        assertNotNull(document);
        assertTrue(document.getFirstChild() != null);
        
        // 2. Validate Sequence of Blocks
        Node current = document.getFirstChild();
        
        assertTrue(current instanceof YamlFrontMatterBlock, "Frontmatter is the first block");
        current = current.getNext();
        
        // Node 1: Heading (# Nickel Mining)
        assertTrue(current instanceof Heading, "First node should be a Heading");
        assertEquals(1, ((Heading) current).getLevel());
        current = current.getNext();
        
        // Node 2: Paragraph (Nickel is found...)
        assertTrue(current instanceof Paragraph, "Second node should be a Paragraph");
        current = current.getNext();
        
        // Node 3: Custom Callout Block
        assertTrue(current instanceof MdxComponentBlock.CalloutBlock, "Third node should be a CalloutBlock");
        MdxComponentBlock.CalloutBlock callout = (MdxComponentBlock.CalloutBlock) current;
        assertEquals("info", callout.variant);
        current = current.getNext();
        
        // Node 4: Heading (## Crafting)
        assertTrue(current instanceof Heading, "Fourth node should be a Heading level 2");
        assertEquals(2, ((Heading) current).getLevel());
        current = current.getNext();
        
        // Node 5: Custom Recipe Block
        assertTrue(current instanceof MdxComponentBlock.CraftingRecipeBlock, "Fifth node should be a RecipeBlock");
        MdxComponentBlock.CraftingRecipeBlock recipe = (MdxComponentBlock.CraftingRecipeBlock) current;
        assertEquals("oritech:nickel_block", recipe.result);
        current = current.getNext();
        
        // Node 6: Bullet List
        assertTrue(current instanceof BulletList, "Sixth node should be a BulletList");
    }
}