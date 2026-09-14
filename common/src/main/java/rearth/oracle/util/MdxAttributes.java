package rearth.oracle.util;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public record MdxAttributes(Map<String, String> values, Set<String> flags) {
    public static final MdxAttributes EMPTY = new MdxAttributes(Map.of(), Set.of());

    public record Match(MdxAttributes attributes, String remainder) {
    }

    public static MdxAttributes parse(String content) {
        if (content == null || content.isBlank()) return EMPTY;

        Map<String, String> values = new HashMap<>();
        Set<String> flags = new HashSet<>();

        for (var token : content.trim().split("\\s+")) {
            if (token.isEmpty()) continue;

            int separator = token.indexOf('=');
            if (separator > 0) {
                String key = token.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String value = unwrap(token.substring(separator + 1).trim());
                if (!key.isEmpty()) {
                    values.put(key, value);
                }
            } else if (token.startsWith("#") && token.length() > 1) {
                values.put("id", token.substring(1));
            } else {
                flags.add(token.toLowerCase(Locale.ROOT));
            }
        }

        return new MdxAttributes(values, flags);
    }

    @Nullable
    public static Match matchLeading(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '{') return null;

        int end = text.indexOf('}');
        if (end < 0) return null;

        return new Match(parse(text.substring(1, end)), text.substring(end + 1));
    }

    @Nullable
    public static Match matchTrailing(@Nullable String text) {
        if (text == null) return null;

        String trimmed = text.stripTrailing();
        if (!trimmed.endsWith("}")) return null;

        int start = trimmed.lastIndexOf('{');
        if (start < 0) return null;

        String inside = trimmed.substring(start + 1, trimmed.length() - 1);
        if (inside.indexOf('{') >= 0 || inside.indexOf('}') >= 0) return null;

        return new Match(parse(inside), trimmed.substring(0, start).stripTrailing());
    }

    private static String unwrap(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if (first == '"' && last == '"' || first == '\'' && last == '\'') {
                return value.substring(1, value.length() - 1);
            }
        }

        if (value.length() >= 2 && value.charAt(0) == '{' && value.charAt(value.length() - 1) == '}') {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }

    public boolean has(String flag) {
        return flags.contains(flag);
    }

    @Nullable
    public String get(String key) {
        return values.get(key);
    }

    @Nullable
    public Integer getPixels(String key) {
        var raw = values.get(key);
        if (raw == null) return null;

        var cleaned = raw.trim().toLowerCase(Locale.ROOT);
        if (cleaned.endsWith("px")) cleaned = cleaned.substring(0, cleaned.length() - 2);

        try {
            return Integer.parseInt(cleaned.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isEmpty() {
        return values.isEmpty() && flags.isEmpty();
    }
}
