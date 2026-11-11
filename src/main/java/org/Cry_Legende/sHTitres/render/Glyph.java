package org.Cry_Legende.sHTitres.render;

/**
 * Un caractère affiché d'un titre, avec sa couleur et ses décorations.
 *
 * @param text  le caractère (un point de code, parfois deux char)
 * @param rgb   couleur 0xRRGGBB
 * @param flags décorations, voir les constantes
 */
public record Glyph(String text, int rgb, int flags) {

    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int UNDERLINED = 4;
    public static final int STRIKETHROUGH = 8;
    public static final int OBFUSCATED = 16;

    public Glyph withColor(int newRgb) {
        return newRgb == rgb ? this : new Glyph(text, newRgb, flags);
    }

    /** Espace dont la couleur ne se voit pas (ni souligné ni barré). */
    public boolean invisibleSpace() {
        return text.isBlank() && (flags & (UNDERLINED | STRIKETHROUGH)) == 0;
    }
}
