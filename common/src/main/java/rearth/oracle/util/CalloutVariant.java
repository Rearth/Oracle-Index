package rearth.oracle.util;

import net.minecraft.network.chat.Component;
import rearth.oracle.ui.widgets.WikiSurface;

import java.util.Locale;

public enum CalloutVariant {
    NOTE(WikiSurface.BEDROCK_PANEL_NOTE),
    TIP(WikiSurface.BEDROCK_PANEL_PRESSED),
    IMPORTANT(WikiSurface.BEDROCK_PANEL_IMPORTANT),
    WARNING(WikiSurface.BEDROCK_PANEL_WARNING),
    CAUTION(WikiSurface.BEDROCK_PANEL_DANGER);

    private final WikiSurface surface;

    CalloutVariant(WikiSurface surface) {
        this.surface = surface;
    }

    public WikiSurface getSurface() {
        return surface;
    }

    public Component getTitle() {
        return Component.translatable("oracle_index.callout." + name().toLowerCase(Locale.ROOT));
    }

    public static CalloutVariant byName(String name, CalloutVariant fallback) {
        if (name == null) return fallback;
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "default", "info", "note" -> NOTE;
            case "important" -> IMPORTANT;
            case "tip" -> TIP;
            case "warning" -> WARNING;
            case "danger", "caution", "error" -> CAUTION;
            default -> fallback;
        };
    }
}
