package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import rearth.oracle.Oracle;

/**
 * Predefined surface backgrounds drawn behind a {@link UIComponent}.
 * Each entry is a 9-patch texture from the oracle resource pack.
 */
public enum WikiSurface {
    
    NONE(null),
    BEDROCK_PANEL(ninePatch("bedrock_panel")),
    BEDROCK_PANEL_HOVER(ninePatch("bedrock_panel_hover")),
    BEDROCK_PANEL_PRESSED(ninePatch("bedrock_panel_pressed")),
    BEDROCK_PANEL_NOTE(ninePatch("bedrock_panel_note")),
    BEDROCK_PANEL_WARNING(ninePatch("bedrock_panel_warning")),
    BEDROCK_PANEL_DANGER(ninePatch("bedrock_panel_danger")),
    BEDROCK_PANEL_DARK(ninePatch("bedrock_panel_dark")),
    BEDROCK_PANEL_DISABLED(ninePatch("bedrock_panel_disabled"));
    
    @FunctionalInterface
    public interface Renderer {
        void render(GuiGraphicsExtractor context, int x, int y, int width, int height);
    }
    
    private final Renderer renderer;
    
    WikiSurface(Renderer renderer) {
        this.renderer = renderer;
    }
    
    public void render(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        if (renderer != null && width > 0 && height > 0) {
            renderer.render(context, x, y, width, height);
        }
    }
    
    public boolean isNone() {
        return renderer == null;
    }
    
    private static Renderer ninePatch(String name) {
        var id = Identifier.fromNamespaceAndPath(Oracle.MOD_ID, "textures/gui/" + name + ".png");
        var renderer = new NinePatchRenderer(id);
        return renderer::render;
    }
}
