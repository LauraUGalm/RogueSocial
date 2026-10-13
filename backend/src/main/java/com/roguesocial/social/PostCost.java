package com.roguesocial.social;

import java.text.BreakIterator;
import java.util.Locale;

/**
 * What a post costs: one gold for every visible symbol that is not whitespace (README section 2).
 *
 * <p>A "symbol" is what a reader sees as one character, which is not what Java counts as one.
 * {@code "👍🏽"} is two code points (thumbs up, then skin tone) and four Java {@code char}s, but it
 * is one symbol. The same goes for flags, family emoji joined with zero-width joiners, and letters
 * with combining accents. Unicode calls these grapheme clusters, and {@link BreakIterator} finds
 * them.
 */
public final class PostCost {

    private PostCost() {
    }

    public static int of(String text) {
        BreakIterator symbols = BreakIterator.getCharacterInstance(Locale.ROOT);
        symbols.setText(text);
        int cost = 0;
        for (int start = symbols.first(), end = symbols.next(); end != BreakIterator.DONE;
                start = end, end = symbols.next()) {
            if (!isSpace(text.substring(start, end))) {
                cost++;
            }
        }
        return cost;
    }

    /** Spaces, tabs and line breaks, plus no-break and other Unicode spaces. */
    private static boolean isSpace(String symbol) {
        return symbol.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
    }
}
