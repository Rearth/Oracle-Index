package rearth.oracle.util;

import rearth.oracle.ui.widgets.WikiSurface;

public enum CalloutVariant {
    DEFAULT(WikiSurface.BEDROCK_PANEL_NOTE),
    INFO(WikiSurface.BEDROCK_PANEL_PRESSED),
    WARNING(WikiSurface.BEDROCK_PANEL_WARNING),
    DANGER(WikiSurface.BEDROCK_PANEL_DANGER);
    
    private final WikiSurface surface;

    CalloutVariant(WikiSurface surface) {
        this.surface = surface;
    }

    public WikiSurface getSurface() {
        return surface;
    }
}
