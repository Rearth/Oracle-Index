package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Renders a 9-patch (9-slice) texture at any size by stretching the centre
 * while keeping corners and edges at their original size.
 *
 * <pre>
 * [TL] [T ] [TR]
 * [L ] [C ] [R ]
 * [BL] [B ] [BR]
 * </pre>
 */
public record NinePatchRenderer(Identifier texture, int texWidth, int texHeight, int cornerWidth, int cornerHeight) {
    
    /**
     * Default for the bedrock panels: 16x16 texture, 4x4 corners.
     */
    public NinePatchRenderer(Identifier texture) {
        this(texture, 16, 16, 4, 4);
    }
    
    public void render(DrawContext context, int x, int y, int width, int height) {
        int cw = cornerWidth;
        int ch = cornerHeight;
        int centerW = texWidth - cw * 2;
        int centerH = texHeight - ch * 2;
        int stretchW = Math.max(0, width - cw * 2);
        int stretchH = Math.max(0, height - ch * 2);
        
        // 4 corners (1:1 source → destination)
        corner(context, x, y, 0, 0);
        corner(context, x + width - cw, y, texWidth - cw, 0);
        corner(context, x, y + height - ch, 0, texHeight - ch);
        corner(context, x + width - cw, y + height - ch, texWidth - cw, texHeight - ch);
        
        // edges (stretch one axis)
        if (stretchW > 0) {
            stretched(context, x + cw, y, cw, 0, stretchW, ch, centerW, ch);
            stretched(context, x + cw, y + height - ch, cw, texHeight - ch, stretchW, ch, centerW, ch);
        }
        if (stretchH > 0) {
            stretched(context, x, y + ch, 0, ch, cw, stretchH, cw, centerH);
            stretched(context, x + width - cw, y + ch, texWidth - cw, ch, cw, stretchH, cw, centerH);
        }
        
        // centre (stretch both axes)
        if (stretchW > 0 && stretchH > 0) {
            stretched(context, x + cw, y + ch, cw, ch, stretchW, stretchH, centerW, centerH);
        }
    }
    
    private void corner(DrawContext context, int dx, int dy, int u, int v) {
        // 9-arg drawTexture: (id, x, y, float u, float v, w, h, texW, texH) - source 1:1 to dest.
        context.drawTexture(texture, dx, dy, (float) u, (float) v, cornerWidth, cornerHeight, texWidth, texHeight);
    }
    
    private void stretched(DrawContext context, int dx, int dy, int u, int v, int dw, int dh, int regW, int regH) {
        // 11-arg drawTexture: (id, x, y, w, h, float u, float v, regW, regH, texW, texH) - source (regW,regH) stretched to (dw,dh).
        context.drawTexture(texture, dx, dy, dw, dh, (float) u, (float) v, regW, regH, texWidth, texHeight);
    }
}
