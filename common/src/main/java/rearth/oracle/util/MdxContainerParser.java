package rearth.oracle.util;

import org.commonmark.node.Block;
import org.commonmark.parser.block.*;
import java.util.regex.Pattern;

public class MdxContainerParser extends AbstractBlockParser {
    
    private final MdxComponentBlock block;
    private final Pattern endPattern;
    
    public MdxContainerParser(MdxComponentBlock block, String tagName, String firstLine) {
        this.block = block;
        this.endPattern = Pattern.compile(".*(</" + tagName + ">).*", Pattern.CASE_INSENSITIVE);
        
        // containers don't collect any content, and assume any "metadata", like callout-variants are defined in the first line
        block.setupContent(firstLine);
    }
    
    @Override
    public boolean isContainer() { return true; }
    
    @Override
    public boolean canContain(Block childBlock) { return true; }
    
    @Override
    public Block getBlock() { return block; }
    
    @Override
    public BlockContinue tryContinue(ParserState state) {
        CharSequence line = state.getLine().getContent();
        
        if (endPattern.matcher(line).matches()) {
            return BlockContinue.finished();
        }
        
        return BlockContinue.atIndex(state.getIndex());
    }
}