package org.Cry_Legende.sHTitres.render;

/**
 * Titre prêt à afficher, en codes &amp; et &amp;#RRGGBB : une version fixe (chat, menus) et les images de son
 * animation (nametag). Toutes les images sont calculées au chargement ; lire l'image courante ne coûte rien.
 */
public final class Rendering {

    private final String still;
    private final String[] frames;
    private final long frameMillis;

    public Rendering(String still, String[] frames, long frameMillis) {
        this.still = still;
        this.frames = frames;
        this.frameMillis = Math.max(1, frameMillis);
    }

    /** Version fixe du titre. */
    public String still() {
        return still;
    }

    public boolean animated() {
        return frames.length > 1;
    }

    /** Image de l'animation à cet instant ; tous les joueurs qui portent le titre sont synchronisés. */
    public String current() {
        if (frames.length == 1) {
            return still;
        }
        return frames[(int) ((System.currentTimeMillis() / frameMillis) % frames.length)];
    }

    public String[] frames() {
        return frames.clone();
    }

    public long frameMillis() {
        return frameMillis;
    }
}
