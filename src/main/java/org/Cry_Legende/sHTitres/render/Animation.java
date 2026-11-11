package org.Cry_Legende.sHTitres.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Animation du texte d'un titre (les bords ne bougent pas).
 *
 * @param type      wave, shine ou pulse
 * @param seconds   durée d'un cycle
 * @param highlight couleur du reflet (shine) ou de l'éclat (pulse)
 * @param strength  force du reflet ou de l'éclat, de 0 à 1
 */
public record Animation(Type type, double seconds, int highlight, double strength) {

    public enum Type {
        /** Le dégradé du texte défile en boucle, d'un bord à l'autre. */
        WAVE,
        /** Un reflet traverse le texte, puis une pause. */
        SHINE,
        /** Tout le texte s'éclaire puis revient, comme un battement. */
        PULSE;

        public static Type parse(String value) {
            if (value == null) {
                return null;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Part du cycle prise par le passage du reflet ; le reste est une pause. */
    private static final double SHINE_SWEEP = 0.5;
    /** Demi-largeur du reflet, en caractères. */
    private static final double SHINE_HALF_WIDTH = 2.2;

    /** Nombre d'images d'un cycle pour une durée d'image donnée. */
    public int frameCount(long frameMillis) {
        return Math.max(2, (int) Math.round(seconds * 1000.0 / frameMillis));
    }

    /** Couleurs du texte à l'image frame sur count. L'image 0 rend toujours le texte d'origine. */
    public List<Glyph> apply(List<Glyph> text, int frame, int count) {
        return switch (type) {
            case WAVE -> wave(text, frame, count);
            case SHINE -> shine(text, frame, count);
            case PULSE -> pulse(text, frame, count);
        };
    }

    private static List<Glyph> wave(List<Glyph> text, int frame, int count) {
        int length = text.size();
        if (length < 2) {
            return text;
        }
        // Aller-retour du dégradé (a..b..a) : la boucle n'a pas de saut de couleur
        int period = length * 2;
        int[] sequence = new int[period];
        for (int k = 0; k < period; k++) {
            sequence[k] = text.get(k < length ? k : period - 1 - k).rgb();
        }
        double offset = (double) frame / count * period;
        List<Glyph> out = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            double position = ((i - offset) % period + period) % period;
            int k0 = (int) Math.floor(position);
            int k1 = (k0 + 1) % period;
            double fraction = position - k0;
            out.add(text.get(i).withColor(Colors.lerp(sequence[k0 % period], sequence[k1], fraction)));
        }
        return out;
    }

    private List<Glyph> shine(List<Glyph> text, int frame, int count) {
        int sweepFrames = Math.max(2, (int) Math.round(count * SHINE_SWEEP));
        if (frame >= sweepFrames) {
            return text;
        }
        double start = -SHINE_HALF_WIDTH;
        double end = text.size() - 1 + SHINE_HALF_WIDTH;
        double position = start + (end - start) * frame / (sweepFrames - 1);
        List<Glyph> out = new ArrayList<>(text.size());
        for (int i = 0; i < text.size(); i++) {
            double k = Math.max(0, 1 - Math.abs(i - position) / SHINE_HALF_WIDTH);
            k = k * k * (3 - 2 * k) * strength;
            Glyph glyph = text.get(i);
            out.add(glyph.withColor(Colors.lerp(glyph.rgb(), highlight, k)));
        }
        return out;
    }

    private List<Glyph> pulse(List<Glyph> text, int frame, int count) {
        double k = strength * (0.5 - 0.5 * Math.cos(2 * Math.PI * frame / count));
        List<Glyph> out = new ArrayList<>(text.size());
        for (Glyph glyph : text) {
            out.add(glyph.withColor(Colors.lerp(glyph.rgb(), highlight, k)));
        }
        return out;
    }
}
