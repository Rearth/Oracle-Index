package rearth.oracle.util;

import org.commonmark.node.CustomNode;
import org.commonmark.parser.beta.*;

import java.util.Set;

public class HoverText extends CustomNode {
    private final String label;
    private final String hint;

    public HoverText(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public String getLabel() {
        return label;
    }

    public String getHint() {
        return hint;
    }

    @Override
    public String toString() {
        return "HoverText{label='" + label + "', hint='" + hint + "'}";
    }

    public static class ParserFactory implements InlineContentParserFactory {
        @Override
        public Set<Character> getTriggerCharacters() {
            return Set.of('?');
        }

        @Override
        public InlineContentParser create() {
            return new Parser();
        }
    }

    private static class Parser implements InlineContentParser {
        @Override
        public ParsedInline tryParse(InlineParserState state) {
            Scanner scanner = state.scanner();
            scanner.next();
            if (!scanner.next('[')) return ParsedInline.none();

            Position labelStart = scanner.position();
            if (scanner.find(']') < 0) return ParsedInline.none();
            String label = scanner.getSource(labelStart, scanner.position()).getContent();
            scanner.next();

            if (!scanner.next('(')) return ParsedInline.none();
            Position hintStart = scanner.position();
            if (scanner.find(')') < 0) return ParsedInline.none();
            String hint = scanner.getSource(hintStart, scanner.position()).getContent();
            scanner.next();

            if (label.isEmpty() || hint.isEmpty()) return ParsedInline.none();
            return ParsedInline.of(new HoverText(label, hint), scanner.position());
        }
    }
}
