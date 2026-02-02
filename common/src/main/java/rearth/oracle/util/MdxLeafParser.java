package rearth.oracle.util;

import org.commonmark.node.Block;
import org.commonmark.parser.block.*;
import java.util.regex.Pattern;

// a leaf is something like an image or a crafting recipe, something that does not have any further children in the tree
public class MdxLeafParser extends AbstractBlockParser {
    
    private final MdxComponentBlock block;
    private final StringBuilder content = new StringBuilder();
    private final Pattern endPattern;
    
    public MdxLeafParser(MdxComponentBlock block, String tagName, String firstLine) {
        this.block = block;
        
        // Matches "/>" OR "</TagName>"
        this.endPattern = Pattern.compile(".*(/>|</" + tagName + ">).*", Pattern.CASE_INSENSITIVE);
        
        // first line needs to be included because it's already passed in the continue capture blocks.
        content.append(firstLine).append("\n");
    }
    
    @Override
    public Block getBlock() { return block; }
    
    @Override
    public BlockContinue tryContinue(ParserState state) {
        var line = state.getLine().getContent();
        
        // collect content
        content.append(line).append("\n");
        
        if (endPattern.matcher(line).matches()) {
            return BlockContinue.finished();
        }
        
        return BlockContinue.atIndex(state.getIndex());
    }
    
    @Override
    public void closeBlock() {
        block.setupContent(content.toString());
    }
}