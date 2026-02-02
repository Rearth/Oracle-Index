package rearth.oracle.util;

import org.commonmark.parser.block.*;

public class MdxBlockFactory extends AbstractBlockParserFactory {

    @Override
    public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
        String line = state.getLine().getContent().toString().trim();

        // leaf blocks
        if (line.startsWith("<CraftingRecipe")) {
            return startLeaf(new MdxComponentBlock.CraftingRecipeBlock(), "CraftingRecipe", line, state);
        } 
        else if (line.startsWith("<ModAsset")) {
            return startLeaf(new MdxComponentBlock.AssetBlock(true), "ModAsset", line, state);
        } 
        else if (line.startsWith("<Asset")) {
            return startLeaf(new MdxComponentBlock.AssetBlock(false), "Asset", line, state);
        } 
        else if (line.startsWith("<PrefabObtaining")) {
             return startLeaf(new MdxComponentBlock.PrefabObtainingBlock(), "PrefabObtaining", line, state);
        }

        // container blocks
        if (line.startsWith("<Callout")) {
            return startContainer(new MdxComponentBlock.CalloutBlock(), "Callout", line, state);
        }

        return BlockStart.none();
    }

    private BlockStart startLeaf(MdxComponentBlock block, String tagName, String line, ParserState state) {
        return BlockStart.of(new MdxLeafParser(block, tagName, line))
                 .atIndex(state.getLine().getContent().length());
    }

    private BlockStart startContainer(MdxComponentBlock block, String tagName, String line, ParserState state) {
        return BlockStart.of(new MdxContainerParser(block, tagName, line))
                 .atIndex(state.getLine().getContent().length());
    }
}