package com.maxhayim.retrodeck.core;

import java.text.Bidi;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lays out text for a segment display, one character per cell, in the order it is seen.
 * <p>
 * Latin capitals, digits and a little punctuation are drawn in segments, accents dropped. Anything else
 * that is printable (Japanese, Chinese, Korean, Hebrew, Arabic, Cyrillic, Greek, symbols) is drawn in the
 * system font inside the cell, so a song title is never blanked out; Chinese, Japanese and Korean
 * characters are wide and take two cells. Right-to-left text such as Hebrew is put in visual order.
 * Text longer than the display scrolls: it rests at the start, steps one cell at a time, then rests at
 * the end before starting over.
 */
final class SegmentText {

    /** How long the text rests at each end, and how long each scroll step shows. */
    static final long HOLD_MS = 2500, STEP_MS = 350;

    /** A character cell: a segment-font character, or a glyph from the system font (span 1 or 2). */
    static final class Cell {
        /** The DSEG character; '!' (a blank cell) when the cell holds a glyph or a space. */
        final char seg;
        /** The system-font text drawn in the cell, or null. */
        final String glyph;
        final int span;

        Cell(char seg, String glyph, int span) {
            this.seg = seg;
            this.glyph = glyph;
            this.span = span;
        }

        @Override
        public String toString() {
            return glyph != null ? "[" + glyph + "]" : String.valueOf(seg);
        }
    }

    private SegmentText() {}

    /** The cells of s, left to right as displayed. */
    static List<Cell> cells(String s) {
        List<Cell> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        String text = visual(Normalizer.normalize(s, Normalizer.Form.NFC));
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                    || type == Character.COMBINING_SPACING_MARK) {
                // A mark left on its own (Hebrew points, say): it belongs to the glyph before it.
                Cell last = out.isEmpty() ? null : out.get(out.size() - 1);
                if (last != null && last.glyph != null) {
                    out.set(out.size() - 1, new Cell('!', last.glyph + new String(Character.toChars(cp)), last.span));
                }
                continue;
            }
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                if (cp == 0x3000) {
                    out.add(new Cell('!', null, 1));
                    out.add(new Cell('!', null, 1));
                } else {
                    out.add(new Cell('!', null, 1));
                }
                continue;
            }
            if (Character.isISOControl(cp) || type == Character.FORMAT) continue;
            char seg = segment(cp);
            if (seg != 0) {
                out.add(new Cell(seg, null, 1));
            } else {
                String g = new String(Character.toChars(cp)).toUpperCase(Locale.ROOT);
                out.add(new Cell('!', g, wide(cp) ? 2 : 1));
            }
        }
        return out;
    }

    /** The segment character for cp, or 0 when it needs the system font. */
    static char segment(int cp) {
        if (cp >= 'a' && cp <= 'z') return (char) (cp - 32);
        if (cp >= 'A' && cp <= 'Z' || cp >= '0' && cp <= '9' || ".:-/'+*()?,".indexOf(cp) >= 0) return (char) cp;
        // Latin letters with accents: Latin-1, Latin Extended A and B, Latin Extended Additional.
        if (!(cp >= 0xC0 && cp <= 0x24F || cp >= 0x1E00 && cp <= 0x1EFF)) return 0;
        // é, ñ, ö...: the letter without its accent, as a segment display would show it.
        String base = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD);
        int b = base.codePointAt(0);
        if (b >= 'a' && b <= 'z') return (char) (b - 32);
        if (b >= 'A' && b <= 'Z') return (char) b;
        return 0;
    }

    /** Chinese, Japanese and Korean characters, and full-width forms, are about square. */
    static boolean wide(int cp) {
        if (cp >= 0x1100 && cp <= 0x115F || cp >= 0x2E80 && cp <= 0x303E || cp >= 0x3041 && cp <= 0x33FF
                || cp >= 0x3400 && cp <= 0x4DBF || cp >= 0x4E00 && cp <= 0x9FFF || cp >= 0xA960 && cp <= 0xA97F
                || cp >= 0xAC00 && cp <= 0xD7A3 || cp >= 0xF900 && cp <= 0xFAFF || cp >= 0xFE30 && cp <= 0xFE4F
                || cp >= 0xFF00 && cp <= 0xFF60 || cp >= 0xFFE0 && cp <= 0xFFE6 || cp >= 0x1F300 && cp <= 0x1FAFF
                || cp >= 0x20000 && cp <= 0x3FFFD) {
            return true;
        }
        return false;
    }

    /** Puts right-to-left runs (Hebrew, Arabic) in the order they're seen, brackets mirrored. */
    static String visual(String s) {
        if (!Bidi.requiresBidi(s.toCharArray(), 0, s.length())) return s;
        Bidi bidi = new Bidi(s, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        int n = bidi.getRunCount();
        byte[] levels = new byte[n];
        Object[] runs = new Object[n];
        for (int r = 0; r < n; r++) {
            int start = bidi.getRunStart(r), limit = bidi.getRunLimit(r);
            levels[r] = (byte) bidi.getRunLevel(r);
            String run = s.substring(start, limit);
            runs[r] = (levels[r] & 1) == 1 ? reverse(run) : run;
        }
        Bidi.reorderVisually(levels, 0, runs, 0, n);
        StringBuilder b = new StringBuilder();
        for (Object r : runs) b.append((String) r);
        return b.toString();
    }

    /** A right-to-left run backwards, keeping each letter's marks after it and mirroring brackets. */
    private static String reverse(String run) {
        List<String> clusters = new ArrayList<>();
        for (int i = 0; i < run.length(); ) {
            int cp = run.codePointAt(i);
            int end = i + Character.charCount(cp);
            while (end < run.length() && Character.getType(run.codePointAt(end)) == Character.NON_SPACING_MARK) {
                end += Character.charCount(run.codePointAt(end));
            }
            clusters.add(mirror(run.substring(i, end)));
            i = end;
        }
        StringBuilder b = new StringBuilder();
        for (int i = clusters.size() - 1; i >= 0; i--) b.append(clusters.get(i));
        return b.toString();
    }

    private static String mirror(String c) {
        switch (c) {
            case "(": return ")";
            case ")": return "(";
            case "[": return "]";
            case "]": return "[";
            case "{": return "}";
            case "}": return "{";
            case "<": return ">";
            case ">": return "<";
            default: return c;
        }
    }

    /** Total width in cells. */
    static int width(List<Cell> cells) {
        int w = 0;
        for (Cell c : cells) w += c.span;
        return w;
    }

    /**
     * How many cells the text has scrolled by at time t (ms) when it is total cells wide and the display
     * shows visible cells. atEnd shows the end, for still screenshots.
     */
    static int scroll(int total, int visible, long t, boolean atEnd) {
        int over = total - visible;
        if (over <= 0) return 0;
        if (atEnd) return over;
        long cycle = 2 * HOLD_MS + over * STEP_MS;
        long p = ((t % cycle) + cycle) % cycle;
        if (p < HOLD_MS) return 0;
        return (int) Math.min(over, (p - HOLD_MS) / STEP_MS + 1);
    }

    /**
     * The cells shown in a window of visible cells starting offset cells in. A wide glyph cut by an edge
     * shows as blank cells. Returns one entry per display cell: the Cell starting there, or null.
     */
    static Cell[] window(List<Cell> cells, int offset, int visible) {
        Cell[] out = new Cell[visible];
        int col = 0;
        for (Cell c : cells) {
            int start = col - offset;
            col += c.span;
            if (start < 0 || start + c.span > visible) continue;
            out[start] = c;
        }
        return out;
    }
}
