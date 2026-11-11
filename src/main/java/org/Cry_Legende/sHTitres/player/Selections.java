package org.Cry_Legende.sHTitres.player;

import me.clip.placeholderapi.PlaceholderAPI;
import org.Cry_Legende.sHTitres.SHTitres;
import org.Cry_Legende.sHTitres.render.Colors;
import org.Cry_Legende.sHTitres.render.Rendering;
import org.Cry_Legende.sHTitres.storage.Storage;
import org.Cry_Legende.sHTitres.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Titre porté par chaque joueur connecté. Les placeholders lisent seulement ce cache (n'importe quel thread) ; la
 * permission et la couleur adaptative sont revérifiées sur le thread principal (connexion, choix, minuterie).
 */
public final class Selections {

    /** Titre porté et son rendu courant. */
    public static final class Worn {
        private final String titleId;
        private final boolean variant;
        private volatile boolean owned;
        private volatile Rendering rendering;

        Worn(String titleId, boolean variant) {
            this.titleId = titleId;
            this.variant = variant;
        }

        public String titleId() {
            return titleId;
        }

        public boolean variant() {
            return variant;
        }

        public boolean owned() {
            return owned;
        }
    }

    private final SHTitres plugin;
    /** Choix lus en base à la pré-connexion, en attente de l'arrivée du joueur. */
    private final Map<UUID, Optional<Storage.Choice>> loaded = new ConcurrentHashMap<>();
    private final Map<UUID, Worn> worn = new ConcurrentHashMap<>();

    public Selections(SHTitres plugin) {
        this.plugin = plugin;
    }

    /** Pré-connexion (thread asynchrone) : lecture en base. */
    public void preload(UUID uuid) {
        try {
            loaded.put(uuid, Optional.ofNullable(plugin.storage().load(uuid)));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Lecture du titre de " + uuid + " impossible", e);
        }
    }

    /** Arrivée du joueur (thread principal). */
    public void join(Player player) {
        Optional<Storage.Choice> choice = loaded.remove(player.getUniqueId());
        if (choice != null) {
            choice.ifPresent(value -> attach(player, value));
            return;
        }
        // Joueur déjà là avant l'activation du plugin, ou lecture ratée à la pré-connexion
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            preload(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Optional<Storage.Choice> late = loaded.remove(uuid);
                if (late != null && player.isOnline()) {
                    late.ifPresent(value -> attach(player, value));
                }
            });
        });
    }

    public void quit(UUID uuid) {
        worn.remove(uuid);
        loaded.remove(uuid);
    }

    private void attach(Player player, Storage.Choice choice) {
        Worn state = new Worn(choice.titleId(), choice.variant());
        worn.put(player.getUniqueId(), state);
        refresh(player, state);
    }

    /** Revérifie la permission et la couleur adaptative d'un joueur (thread principal). */
    public void refresh(Player player) {
        Worn state = worn.get(player.getUniqueId());
        if (state != null) {
            refresh(player, state);
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    private void refresh(Player player, Worn state) {
        Title title = plugin.titles().get(state.titleId);
        if (title == null) {
            state.owned = false;
            state.rendering = null;
            return;
        }
        state.owned = player.hasPermission(title.permission());
        int color = state.variant && title.variantKind() == Title.VariantKind.ADAPTIVE ? adaptiveColor(player) : -1;
        state.rendering = title.rendering(state.variant, color);
    }

    private int adaptiveColor(Player player) {
        String placeholder = plugin.settings().adaptiveColorPlaceholder;
        if (placeholder.isEmpty() || !plugin.hasPlaceholderApi()) {
            return -1;
        }
        return Colors.lastColor(PlaceholderAPI.setPlaceholders(player, placeholder));
    }

    /** Titre affiché d'un joueur, null s'il n'en a pas ou n'a plus la permission. */
    public Rendering rendering(UUID uuid) {
        Worn state = worn.get(uuid);
        return state == null || !state.owned ? null : state.rendering;
    }

    /** Titre enregistré d'un joueur connecté (affiché ou non). */
    public Worn worn(UUID uuid) {
        return worn.get(uuid);
    }

    /** Titre affiché d'un joueur, null sinon. */
    public Title shownTitle(UUID uuid) {
        Worn state = worn.get(uuid);
        return state == null || !state.owned ? null : plugin.titles().get(state.titleId);
    }

    /**
     * Pose un titre et l'enregistre. Le futur se termine sur le thread principal, en échec si la base refuse.
     */
    public CompletableFuture<Void> select(Player player, Title title, boolean variant) {
        Worn state = new Worn(title.id(), variant && title.hasVariant());
        worn.put(player.getUniqueId(), state);
        refresh(player, state);
        return write(player.getUniqueId(), new Storage.Choice(state.titleId, state.variant));
    }

    /** Retire le titre d'un joueur connecté et l'efface de la base. */
    public CompletableFuture<Void> clear(Player player) {
        worn.remove(player.getUniqueId());
        return write(player.getUniqueId(), null);
    }

    /** Enregistre (choice) ou efface (null) le titre d'un joueur, connecté ou non. */
    public CompletableFuture<Void> write(UUID uuid, Storage.Choice choice) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (choice == null) {
                    plugin.storage().delete(uuid);
                } else {
                    plugin.storage().save(uuid, choice);
                }
                Bukkit.getScheduler().runTask(plugin, () -> future.complete(null));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Enregistrement du titre de " + uuid + " impossible", e);
                Bukkit.getScheduler().runTask(plugin, () -> future.completeExceptionally(e));
            }
        });
        return future;
    }

    /** Pose un titre à un joueur connecté sans l'enregistrer (déjà fait par l'appelant). */
    public void apply(Player player, Storage.Choice choice) {
        if (choice == null) {
            worn.remove(player.getUniqueId());
        } else {
            attach(player, choice);
        }
    }
}
