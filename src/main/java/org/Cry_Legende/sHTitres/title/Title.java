package org.Cry_Legende.sHTitres.title;

import org.Cry_Legende.sHTitres.render.Rendering;
import org.Cry_Legende.sHTitres.render.TitleStyle;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Un titre de titles.yml, avec ses rendus calculés au chargement.
 */
public final class Title {

    /** Seconde version d'un titre, choisie au clic droit. */
    public enum VariantKind {
        NONE,
        /** Bords à la couleur du joueur. */
        ADAPTIVE,
        /** Autre apparence écrite dans titles.yml. */
        ALTERNATE
    }

    private final String id;
    private final String category;
    private final String permission;
    private final boolean hidden;
    private final String icon;
    private final boolean glint;
    private final List<String> obtain;
    private final TitleStyle style;
    private final Rendering rendering;
    private final VariantKind variantKind;
    private final String variantLabel;
    private final TitleStyle alternateStyle;
    private final Rendering alternateRendering;
    private final long frameMillis;
    private final Map<Integer, Rendering> adaptiveRenderings = new ConcurrentHashMap<>();

    public Title(String id, String category, String permission, boolean hidden, String icon, boolean glint,
                 List<String> obtain, TitleStyle style, VariantKind variantKind, String variantLabel,
                 TitleStyle alternateStyle, long frameMillis) {
        this.id = id;
        this.category = category;
        this.permission = permission;
        this.hidden = hidden;
        this.icon = icon;
        this.glint = glint;
        this.obtain = List.copyOf(obtain);
        this.style = style;
        this.rendering = style.render(frameMillis);
        this.variantKind = variantKind;
        this.variantLabel = variantLabel;
        this.alternateStyle = alternateStyle;
        this.alternateRendering = alternateStyle == null ? null : alternateStyle.render(frameMillis);
        this.frameMillis = frameMillis;
    }

    public String id() {
        return id;
    }

    public String category() {
        return category;
    }

    public String permission() {
        return permission;
    }

    /** Caché du menu tant que le joueur ne le possède pas. */
    public boolean hidden() {
        return hidden;
    }

    public String icon() {
        return icon;
    }

    public boolean glint() {
        return glint;
    }

    public List<String> obtain() {
        return obtain;
    }

    /** Nom sans couleur (« Netflix »). */
    public String name() {
        return style.plainText();
    }

    public VariantKind variantKind() {
        return variantKind;
    }

    public boolean hasVariant() {
        return variantKind != VariantKind.NONE;
    }

    public String variantLabel() {
        return variantLabel;
    }

    public Rendering rendering() {
        return rendering;
    }

    /**
     * Rendu porté par un joueur.
     *
     * @param variant       seconde version choisie
     * @param adaptiveColor couleur du joueur (0xRRGGBB), -1 si inconnue
     */
    public Rendering rendering(boolean variant, int adaptiveColor) {
        if (!variant) {
            return rendering;
        }
        return switch (variantKind) {
            case NONE -> rendering;
            case ALTERNATE -> alternateRendering;
            case ADAPTIVE -> adaptiveColor < 0 ? rendering
                    : adaptiveRenderings.computeIfAbsent(adaptiveColor, rgb -> style.renderWithBorderColor(rgb, frameMillis));
        };
    }

    public boolean animated() {
        return rendering.animated() || (alternateRendering != null && alternateRendering.animated());
    }
}
