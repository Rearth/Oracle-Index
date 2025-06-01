package rearth.oracle.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class Util {
    public static Text getLanguageText(String code) {
        var languageManager = MinecraftClient.getInstance().getLanguageManager();
        var languageDefinition = languageManager.getLanguage(code);
        Text langText = Text.literal(code).formatted(Formatting.BOLD, Formatting.UNDERLINE);  // fallback
        if (languageDefinition != null) {
            langText = languageDefinition.getDisplayText().copy().formatted(Formatting.BOLD);
        }
        return langText;
    }
}
