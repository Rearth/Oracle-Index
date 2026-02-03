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

import java.util.ArrayList;
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
        
        assertInstanceOf(MdxComponentBlock.CraftingRecipeBlock.class, firstChild);
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
        
        assertInstanceOf(MdxComponentBlock.CalloutBlock.class, firstChild);
        MdxComponentBlock.CalloutBlock callout = (MdxComponentBlock.CalloutBlock) firstChild;
        assertEquals("warning", callout.variant);
    }
    
    @Test
    @DisplayName("Lists: Verify nesting depth and numbering sequence logic")
    void testNestedListLogic() {
        String md = """
        1. First
        2. Second
           * Sub A
           * Sub B
        3. Third
        """;
        
        Node document = parser.parse(md);
        
        // We'll track the results in a list of strings: "depth:label"
        List<String> results = new ArrayList<>();
        
        AbstractVisitor testVisitor = new AbstractVisitor() {
            @Override
            public void visit(ListItem listItem) {
                var parent = listItem.getParent();
                
                // Replicating depth logic
                int depth = 0;
                var ancestor = parent;
                while (ancestor instanceof ListBlock || ancestor instanceof ListItem) {
                    if (ancestor instanceof ListBlock) depth++;
                    ancestor = ancestor.getParent();
                }
                
                String label = "";
                if (parent instanceof BulletList) {
                    label = "•";
                } else if (parent instanceof OrderedList orderedList) {
                    int index = 1;
                    Node sibling = listItem.getPrevious();
                    while (sibling != null) {
                        if (sibling instanceof ListItem) index++;
                        sibling = sibling.getPrevious();
                    }
                    label = String.valueOf(orderedList.getStartNumber() + index - 1);
                }
                
                results.add(depth + ":" + label);
                visitChildren(listItem);
            }
        };
        
        document.accept(testVisitor);
        
        // Expected structure:
        // 1:1 (First)
        // 1:2 (Second)
        // 2:• (Sub A - Depth 2)
        // 2:• (Sub B - Depth 2)
        // 1:3 (Third)
        
        assertEquals(5, results.size());
        assertEquals("1:1", results.get(0));
        assertEquals("1:2", results.get(1));
        assertEquals("2:•", results.get(2));
        assertEquals("2:•", results.get(3));
        assertEquals("1:3", results.get(4));
    }
    
    @Test
    @DisplayName("Utility: Image width conversion")
    void testWidthLogic() {
        assertEquals(0.5f, MarkdownParser.convertImageWidth("50%"));
        assertEquals(0.256f, MarkdownParser.convertImageWidth("{256}"));
    }
    
    @Test
    @DisplayName("Formatting: Soft line breaks should be converted to spaces")
    void testSoftLineBreaks() {
        String md = """
        Line One
        Line Two
        """;
        
        // We need a custom visitor for the test since OwoMarkdownVisitor
        // depends on Minecraft/owo classes.
        StringBuilder result = new StringBuilder();
        AbstractVisitor testVisitor = new AbstractVisitor() {
            @Override
            public void visit(org.commonmark.node.Text text) {
                result.append(text.getLiteral());
            }
            
            @Override
            public void visit(SoftLineBreak softLineBreak) {
                result.append(" ");
            }
        };
        
        Node document = parser.parse(md);
        document.accept(testVisitor);
        
        assertEquals("Line One Line Two", result.toString().trim());
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
        assertNotNull(document.getFirstChild());
        
        // 2. Validate Sequence of Blocks
        Node current = document.getFirstChild();
        
        assertInstanceOf(YamlFrontMatterBlock.class, current, "Frontmatter is the first block");
        current = current.getNext();
        
        // Node 1: Heading (# Nickel Mining)
        assertInstanceOf(Heading.class, current, "First node should be a Heading");
        assertEquals(1, ((Heading) current).getLevel());
        current = current.getNext();
        
        // Node 2: Paragraph (Nickel is found...)
        assertInstanceOf(Paragraph.class, current, "Second node should be a Paragraph");
        current = current.getNext();
        
        // Node 3: Custom Callout Block
        assertInstanceOf(MdxComponentBlock.CalloutBlock.class, current, "Third node should be a CalloutBlock");
        MdxComponentBlock.CalloutBlock callout = (MdxComponentBlock.CalloutBlock) current;
        assertEquals("info", callout.variant);
        current = current.getNext();
        
        // Node 4: Heading (## Crafting)
        assertInstanceOf(Heading.class, current, "Fourth node should be a Heading level 2");
        assertEquals(2, ((Heading) current).getLevel());
        current = current.getNext();
        
        // Node 5: Custom Recipe Block
        assertInstanceOf(MdxComponentBlock.CraftingRecipeBlock.class, current, "Fifth node should be a RecipeBlock");
        MdxComponentBlock.CraftingRecipeBlock recipe = (MdxComponentBlock.CraftingRecipeBlock) current;
        assertEquals("oritech:nickel_block", recipe.result);
        current = current.getNext();
        
        // Node 6: Bullet List
        assertInstanceOf(BulletList.class, current, "Sixth node should be a BulletList");
    }
}