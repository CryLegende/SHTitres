package org.Cry_Legende.sHTitres.command;

import org.Cry_Legende.sHTitres.SHTitres;
import org.Cry_Legende.sHTitres.menu.TitleMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** /titres : menu des titres. Les anciens noms des menus ouvrent la même page qu'avant. */
public final class TitresCommand implements CommandExecutor, TabCompleter {

    private final SHTitres plugin;

    public TitresCommand(SHTitres plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande en jeu seulement.");
            return true;
        }
        String alias = label.toLowerCase(Locale.ROOT);
        int colon = alias.indexOf(':');
        if (colon >= 0) {
            alias = alias.substring(colon + 1);
        }
        String category = alias.startsWith("titre_speciaux") ? "speciaux" : "titres";
        int page = alias.equals("titres2") || alias.equals("titre_speciaux2") ? 1 : 0;
        TitleMenu.open(plugin, player, category, page);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                      @NotNull String[] args) {
        return List.of();
    }
}
