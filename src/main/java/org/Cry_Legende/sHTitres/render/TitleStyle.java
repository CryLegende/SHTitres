package org.Cry_Legende.sHTitres.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Apparence d'un titre : bord gauche, texte, bord droit (MiniMessage) et animation éventuelle du texte.
 */
public final class TitleStyle {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final List<Glyph> left;
    private final List<Glyph> text;
    private final List<Glyph> right;
    private final Animation animation;

    private TitleStyle(List<Glyph> left, List<Glyph> text, List<Glyph> right, Animation animation) {
        this.left = List.copyOf(left);
        this.text = List.copyOf(text);
        this.right = List.copyOf(right);
        this.animation = animation;
    }

    /**
     * @throws IllegalArgumentException si le texte est vide
     */
    public static TitleStyle of(String left, String text, String right, Animation animation) {
        List<Glyph> textGlyphs = parse(text);
        if (textGlyphs.isEmpty()) {
            throw new IllegalArgumentException("texte vide");
        }
        return new TitleStyle(parse(left), textGlyphs, parse(right), animation);
    }

    public Animation animation() {
        return animation;
    }

    /** Texte sans couleur ni bords (« Netflix »). */
    public String plainText() {
        StringBuilder builder = new StringBuilder();
        for (Glyph glyph : text) {
            builder.append(glyph.text());
        }
        return builder.toString().trim();
    }

    public Rendering render(long frameMillis) {
        return render(left, right, frameMillis);
    }

    /** Même titre, bords recolorés (couleur adaptative du joueur). */
    public Rendering renderWithBorderColor(int rgb, long frameMillis) {
        return render(recolor(left, rgb), recolor(right, rgb), frameMillis);
    }

    private Rendering render(List<Glyph> leftGlyphs, List<Glyph> rightGlyphs, long frameMillis) {
        String still = serialize(leftGlyphs, text, rightGlyphs);
        if (animation == null) {
            return new Rendering(still, new String[] {still}, frameMillis);
        }
        int count = animation.frameCount(frameMillis);
        String[] frames = new String[count];
        frames[0] = still;
        for (int frame = 1; frame < count; frame++) {
            frames[frame] = serialize(leftGlyphs, animation.apply(text, frame, count), rightGlyphs);
        }
        return new Rendering(still, frames, frameMillis);
    }

    private static List<Glyph> recolor(List<Glyph> glyphs, int rgb) {
        List<Glyph> out = new ArrayList<>(glyphs.size());
        for (Glyph glyph : glyphs) {
            out.add(glyph.withColor(rgb));
        }
        return out;
    }

    /** MiniMessage vers une liste de caractères colorés. */
    static List<Glyph> parse(String miniMessage) {
        List<Glyph> out = new ArrayList<>();
        if (miniMessage == null || miniMessage.isEmpty()) {
            return out;
        }
        flatten(MINI_MESSAGE.deserialize(miniMessage), Style.empty(), out);
        return out;
    }

    private static void flatten(Component component, Style parent, List<Glyph> out) {
        Style style = component.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent textComponent) {
            int rgb = style.color() == null ? 0xFFFFFF : style.color().value();
            int flags = flags(style);
            textComponent.content().codePoints()
                    .forEach(codePoint -> out.add(new Glyph(new String(Character.toChars(codePoint)), rgb, flags)));
        }
        for (Component child : component.children()) {
            flatten(child, style, out);
        }
    }

    private static int flags(Style style) {
        int flags = 0;
        if (style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE) {
            flags |= Glyph.BOLD;
        }
        if (style.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE) {
            flags |= Glyph.ITALIC;
        }
        if (style.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE) {
            flags |= Glyph.UNDERLINED;
        }
        if (style.decoration(TextDecoration.STRIKETHROUGH) == TextDecoration.State.TRUE) {
            flags |= Glyph.STRIKETHROUGH;
        }
        if (style.decoration(TextDecoration.OBFUSCATED) == TextDecoration.State.TRUE) {
            flags |= Glyph.OBFUSCATED;
        }
        return flags;
    }

    /** Caractères colorés vers des codes &amp; et &amp;#RRGGBB, sans répéter un code inchangé. */
    static String serialize(List<Glyph> left, List<Glyph> text, List<Glyph> right) {
        StringBuilder builder = new StringBuilder((left.size() + text.size() + right.size()) * 11);
        int[] state = {-1, 0};
        append(builder, left, state);
        append(builder, text, state);
        append(builder, right, state);
        return builder.toString();
    }

    private static void append(StringBuilder builder, List<Glyph> glyphs, int[] state) {
        for (Glyph glyph : glyphs) {
            // Une espace hérite du style courant : sa couleur ne se voit pas
            if (glyph.invisibleSpace() && state[0] != -1 && (state[1] & Glyph.BOLD) == (glyph.flags() & Glyph.BOLD)) {
                builder.append(glyph.text());
                continue;
            }
            int flags = glyph.flags();
            // Un code couleur efface les décorations : on le remet dès qu'une décoration doit disparaître
            if (glyph.rgb() != state[0] || (state[1] & ~flags) != 0) {
                builder.append("&#").append(Colors.hex(glyph.rgb()));
                state[0] = glyph.rgb();
                state[1] = 0;
            }
            int added = flags & ~state[1];
            if ((added & Glyph.BOLD) != 0) {
                builder.append("&l");
            }
            if ((added & Glyph.ITALIC) != 0) {
                builder.append("&o");
            }
            if ((added & Glyph.UNDERLINED) != 0) {
                builder.append("&n");
            }
            if ((added & Glyph.STRIKETHROUGH) != 0) {
                builder.append("&m");
            }
            if ((added & Glyph.OBFUSCATED) != 0) {
                builder.append("&k");
            }
            state[1] = flags;
            builder.append(glyph.text());
        }
    }
}
