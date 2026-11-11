package org.Cry_Legende.sHTitres.render;

import java.util.Locale;

/** Couleurs : mélange, lecture des codes &amp; et &amp;#RRGGBB. */
public final class Colors {

    private static final int[] LEGACY = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private Colors() {
    }

    public static int lerp(int from, int to, double t) {
        if (t <= 0) {
            return from;
        }
        if (t >= 1) {
            return to;
        }
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** "#RRGGBB" ou "RRGGBB" ; -1 si illisible. */
    public static int parseHex(String value) {
        if (value == null) {
            return -1;
        }
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }

    /**
     * Dernière couleur d'un texte en codes &amp; ou §, en &amp;#RRGGBB ou en &amp;x&amp;R&amp;R&amp;G&amp;G&amp;B&amp;B.
     * Rend -1 s'il n'y en a aucune.
     */
    public static int lastColor(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        String s = text.replace('§', '&');
        int found = -1;
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) != '&') {
                continue;
            }
            char c = Character.toLowerCase(s.charAt(i + 1));
            if (c == '#' && i + 8 <= s.length()) {
                int rgb = parseHex(s.substring(i + 2, i + 8));
                if (rgb >= 0) {
                    found = rgb;
                    i += 7;
                }
            } else if (c == 'x' && i + 14 <= s.length()) {
                StringBuilder hex = new StringBuilder(6);
                for (int k = 0; k < 6; k++) {
                    hex.append(s.charAt(i + 3 + k * 2));
                }
                int rgb = parseHex(hex.toString());
                if (rgb >= 0) {
                    found = rgb;
                    i += 13;
                }
            } else {
                int index = Character.digit(c, 16);
                if (index >= 0) {
                    found = LEGACY[index];
                    i++;
                }
            }
        }
        return found;
    }
}
