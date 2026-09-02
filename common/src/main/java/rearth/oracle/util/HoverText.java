package rearth.oracle.util;

import org.commonmark.node.CustomNode;
import org.commonmark.parser.beta.InlineContentParser;
import org.commonmark.parser.beta.InlineContentParserFactory;
import org.commonmark.parser.beta.InlineParserState;
import org.commonmark.parser.beta.ParsedInline;
import org.commonmark.parser.beta.Scanner;

import java.util.Set;

/**
 * Inline node produced by the wiki's hover text syntax: {@code ?[label](hint shown on hover)}.
 *
 * <p>Unlike a link destination the hint may contain spaces, so this cannot be expressed with
 * plain markdown and needs its own inline parser.</p>
 */
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
            var scanner = state.scanner();
            scanner.next(); // '?'
            if (!scanner.next('[')) return ParsedInline.none();

            var labelStart = scanner.position();
            if (scanner.find(']') < 0) return ParsedInline.none();
            var label = scanner.getSource(labelStart, scanner.position()).getContent();
            scanner.next(); // ']'

            if (!scanner.next('(')) return ParsedInline.none();
            var hintStart = scanner.position();
            if (scanner.find(')') < 0) return ParsedInline.none();
            var hint = scanner.getSource(hintStart, scanner.position()).getContent();
            scanner.next(); // ')'

            if (label.isEmpty() || hint.isEmpty()) return ParsedInline.none();
            return ParsedInline.of(new HoverText(label, hint), scanner.position());
        }
    }
}
