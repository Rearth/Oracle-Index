package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Renders a static texture region. Use {@link #region(int, int, int, int)} to
 * pull a sub-region from the source texture.
 */
public class TextureWidget extends UIComponent {
    
    private final Identifier texture;
    private int u = 0, v = 0;
    private int regionWidth, regionHeight;
    private final int textureWidth;
    private final int textureHeight;
    
    public TextureWidget(Identifier texture, int textureWidth, int textureHeight) {
        super(0, 0, textureWidth, textureHeight);
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.regionWidth = textureWidth;
        this.regionHeight = textureHeight;
    }
    
    public TextureWidget region(int u, int v, int regionWidth, int regionHeight) {
        this.u = u;
        this.v = v;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        return this;
    }
    
    @Override
    protected void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (width == regionWidth && height == regionHeight) {
            // 9-arg: (id, x, y, float u, float v, w, h, texW, texH)
            context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) u, (float) v, width, height, textureWidth, textureHeight);
        } else {
            // 11-arg stretch: (id, x, y, w, h, float u, float v, regW, regH, texW, texH)
            context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) u, (float) v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
        }
    }
}
