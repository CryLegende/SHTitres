package org.Cry_Legende.sHTitres.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.Cry_Legende.sHTitres.SHTitres;
import org.Cry_Legende.sHTitres.render.Rendering;
import org.Cry_Legende.sHTitres.title.Title;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * %shtitres_...%. Ne lit que le cache des joueurs connectés : appelable depuis n'importe quel thread (les nametags
 * sont résolus en asynchrone).
 */
public final class TitresExpansion extends PlaceholderExpansion {

    private final SHTitres plugin;

    public TitresExpansion(SHTitres plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shtitres";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Cry_Legende";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equals("total")) {
            return Integer.toString(plugin.titles().size());
        }
        if (player == null) {
            return "";
        }
        switch (params) {
            case "titre":
                return still(player, false);
            case "titre_espace":
                return still(player, true);
            case "titre_anime":
                return animated(player, false);
            case "titre_anime_espace":
                return animated(player, true);
            case "id": {
                Title title = plugin.selections().shownTitle(player.getUniqueId());
                return title == null ? "" : title.id();
            }
            case "nom": {
                Title title = plugin.selections().shownTitle(player.getUniqueId());
                return title == null ? "" : title.name();
            }
            case "possedes":
                return Integer.toString(owned(player));
            default:
                return null;
        }
    }

    private String still(OfflinePlayer player, boolean space) {
        Rendering rendering = plugin.selections().rendering(player.getUniqueId());
        if (rendering == null) {
            return "";
        }
        return space ? rendering.still() + "&r " : rendering.still();
    }

    private String animated(OfflinePlayer player, boolean space) {
        Rendering rendering = plugin.selections().rendering(player.getUniqueId());
        if (rendering == null) {
            return "";
        }
        return space ? rendering.current() + "&r " : rendering.current();
    }

    private int owned(OfflinePlayer offline) {
        Player player = offline.getPlayer();
        if (player == null) {
            return 0;
        }
        int count = 0;
        for (Title title : plugin.titles().all()) {
            if (player.hasPermission(title.permission())) {
                count++;
            }
        }
        return count;
    }
}
