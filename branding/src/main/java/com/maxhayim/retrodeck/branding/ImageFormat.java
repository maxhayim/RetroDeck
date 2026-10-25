package com.maxhayim.retrodeck.branding;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Recognises image files by their first bytes (never by name) and reads their size from the header,
 * so oversized images are refused before anything tries to decode them.
 */
public final class ImageFormat {

    public static final String PNG = "png", JPEG = "jpeg", WEBP = "webp", SVG = "svg";

    /** Largest width or height accepted from a file or pack. */
    public static final int MAX_DIMENSION = 4096;

    private ImageFormat() {}

    /** The format, or null when the bytes aren't a supported image. */
    public static String sniff(byte[] b, int len) {
        if (len >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return PNG;
        if (len >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return JPEG;
        if (len >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return WEBP;
        if (looksLikeSvg(b, len)) return SVG;
        return null;
    }

    public static String sniff(byte[] b) {
        return sniff(b, b.length);
    }

    private static boolean looksLikeSvg(byte[] b, int len) {
        String head = new String(b, 0, Math.min(len, 1024), StandardCharsets.UTF_8).trim().toLowerCase(Locale.US);
        if (head.startsWith("\uFEFF")) head = head.substring(1);
        return (head.startsWith("<?xml") || head.startsWith("<svg") || head.startsWith("<!--")) && head.contains("<svg");
    }

    /** {width, height} from the header, or null when unknown (always for SVG) or malformed. */
    public static int[] dimensions(byte[] b, int len) {
        String f = sniff(b, len);
        if (PNG.equals(f)) {
            // IHDR is always the first chunk.
            if (len < 24) return null;
            return valid(be32(b, 16), be32(b, 20));
        }
        if (JPEG.equals(f)) return jpeg(b, len);
        if (WEBP.equals(f)) return webp(b, len);
        return null;
    }

    private static int[] jpeg(byte[] b, int len) {
        int i = 2;
        while (i + 9 < len) {
            if ((b[i] & 0xFF) != 0xFF) return null;
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xFF) {
                i++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            int segLen = be16(b, i + 2);
            boolean sof = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (sof) return valid(be16(b, i + 7), be16(b, i + 5));
            if (segLen < 2) return null;
            i += 2 + segLen;
        }
        return null;
    }

    private static int[] webp(byte[] b, int len) {
        if (len < 30) return null;
        String chunk = new String(b, 12, 4, StandardCharsets.US_ASCII);
        switch (chunk) {
            case "VP8 ":
                if ((b[23] & 0xFF) != 0x9D || (b[24] & 0xFF) != 0x01 || (b[25] & 0xFF) != 0x2A) return null;
                return valid(le16(b, 26) & 0x3FFF, le16(b, 28) & 0x3FFF);
            case "VP8L": {
                if ((b[20] & 0xFF) != 0x2F) return null;
                int bits = (b[21] & 0xFF) | (b[22] & 0xFF) << 8 | (b[23] & 0xFF) << 16 | (b[24] & 0xFF) << 24;
                return valid((bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1);
            }
            case "VP8X":
                return valid(le24(b, 24) + 1, le24(b, 27) + 1);
            default:
                return null;
        }
    }

    private static int[] valid(int w, int h) {
        return w > 0 && h > 0 ? new int[]{w, h} : null;
    }

    private static int be32(byte[] b, int i) {
        return (b[i] & 0xFF) << 24 | (b[i + 1] & 0xFF) << 16 | (b[i + 2] & 0xFF) << 8 | (b[i + 3] & 0xFF);
    }

    private static int be16(byte[] b, int i) {
        return (b[i] & 0xFF) << 8 | (b[i + 1] & 0xFF);
    }

    private static int le16(byte[] b, int i) {
        return (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8;
    }

    private static int le24(byte[] b, int i) {
        return (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8 | (b[i + 2] & 0xFF) << 16;
    }

    /**
     * Problems that make an SVG unsafe or unrenderable here: entity declarations (the "billion laughs"
     * attack), scripts, and references to files or the network. Returns null when it's fine.
     */
    public static String svgProblem(String svg) {
        String s = svg.toLowerCase(Locale.US);
        if (s.contains("<!doctype") || s.contains("<!entity")) return "SVG files with a DOCTYPE or entities aren't allowed";
        if (s.contains("<script")) return "SVG files with scripts aren't allowed";
        if (s.contains("<foreignobject")) return "SVG files with embedded HTML aren't allowed";
        if (s.matches("(?s).*href\\s*=\\s*[\"']\\s*(https?|file|content|ftp):.*")) {
            return "SVG files that load other files aren't allowed";
        }
        if (s.matches("(?s).*url\\(\\s*[\"']?\\s*(https?|file|content|ftp):.*")) {
            return "SVG files that load other files aren't allowed";
        }
        return null;
    }

    /**
     * The power-of-two inSampleSize that brings the image near maxDim without going below it, so the
     * decoder never allocates a full-size bitmap of a huge photo.
     */
    public static int sampleSize(int w, int h, int maxDim) {
        int s = 1;
        int big = Math.max(w, h);
        while (big / (s * 2) >= maxDim) s *= 2;
        return s;
    }

    /** {width, height} scaled down (never up) so neither side exceeds maxDim, keeping the aspect. */
    public static int[] fit(int w, int h, int maxDim) {
        if (w <= maxDim && h <= maxDim) return new int[]{w, h};
        float f = maxDim / (float) Math.max(w, h);
        return new int[]{Math.max(1, Math.round(w * f)), Math.max(1, Math.round(h * f))};
    }

    /**
     * The smallest box holding every pixel more than barely visible, as {left, top, right, bottom}, or
     * null when the whole image is transparent. Used to drop empty margins around logos.
     */
    public static int[] opaqueBounds(int[] argb, int w, int h) {
        int l = w, t = h, r = -1, btm = -1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                if ((argb[row + x] >>> 24) > 8) {
                    if (x < l) l = x;
                    if (x > r) r = x;
                    if (y < t) t = y;
                    if (y > btm) btm = y;
                }
            }
        }
        return r < 0 ? null : new int[]{l, t, r + 1, btm + 1};
    }
}
