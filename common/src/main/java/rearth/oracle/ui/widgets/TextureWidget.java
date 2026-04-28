package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Renders a static texture region. Use {@link #region(int, int, int, int)} to
 * pull a sub-region from the source texture.
 */
public class TextureWidget extends UIComponent {
    
    private final Identifier texture;
    private int u = 0, v = 0;
    private int regionWidth, regionHeight;
    private int textureWidth, textureHeight;
    
    public TextureWidget(Identifier texture, int textureWidth, int textureHeight) {
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.regionWidth = textureWidth;
        this.regionHeight = textureHeight;
        size(textureWidth, textureHeight);
    }
    
    public TextureWidget region(int u, int v, int regionWidth, int regionHeight) {
        this.u = u;
        this.v = v;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        return this;
    }
    
    public TextureWidget textureSize(int textureWidth, int textureHeight) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        return this;
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        if (width == regionWidth && height == regionHeight) {
            // 9-arg: (id, x, y, float u, float v, w, h, texW, texH)
            context.drawTexture(texture, x, y, (float) u, (float) v, width, height, textureWidth, textureHeight);
        } else {
            // 11-arg stretch: (id, x, y, w, h, float u, float v, regW, regH, texW, texH)
            context.drawTexture(texture, x, y, width, height, (float) u, (float) v, regionWidth, regionHeight, textureWidth, textureHeight);
        }
    }
}
