package org.Cry_Legende.sHTitres.command;

import org.Cry_Legende.sHTitres.SHTitres;
import org.Cry_Legende.sHTitres.player.Selections;
import org.Cry_Legende.sHTitres.render.Rendering;
import org.Cry_Legende.sHTitres.storage.Storage;
import org.Cry_Legende.sHTitres.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** /shtitres : administration. */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("set", "clear", "info", "list", "reload");

    private final SHTitres plugin;

    public AdminCommand(SHTitres plugin) {
        this.plugin = plugin;
    }

    private static String permission(String subcommand) {
        return "shtitres.admin." + subcommand;
    }

    private List<String> usable(CommandSender sender) {
        List<String> usable = new ArrayList<>();
        for (String subcommand : SUBCOMMANDS) {
            if (sender.hasPermission(permission(subcommand))) {
                usable.add(subcommand);
            }
        }
        return usable;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        List<String> usable = usable(sender);
        if (usable.isEmpty()) {
            plugin.messages().send(sender, "admin-unknown-command");
            return true;
        }
        String subcommand = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (!usable.contains(subcommand)) {
            plugin.messages().send(sender, "admin-usage", Map.of("{subcommands}", String.join("|", usable)));
            return true;
        }
        switch (subcommand) {
            case "set" -> set(sender, args);
            case "clear" -> clear(sender, args);
            case "info" -> info(sender, args);
            case "list" -> list(sender);
            default -> plugin.reload(sender);
        }
        return true;
    }

    /** UUID d'un pseudo : joueur connecté, déjà vu par ce serveur, sinon UUID hors ligne (jamais de requête Mojang). */
    private static UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            return cached.getUniqueId();
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private void set(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "admin-usage-set");
            return;
        }
        Title title = plugin.titles().get(args[2]);
        if (title == null) {
            plugin.messages().send(sender, "admin-unknown-title", Map.of("{id}", args[2]));
            return;
        }
        boolean variant = args.length > 3 && args[3].equalsIgnoreCase("variant") && title.hasVariant();
        String name = args[1];
        UUID uuid = resolve(name);
        Storage.Choice choice = new Storage.Choice(title.id(), variant);
        plugin.selections().write(uuid, choice).whenComplete((ignored, error) -> {
            if (error != null) {
                plugin.messages().send(sender, "admin-storage-error");
                return;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.selections().apply(player, choice);
            }
            plugin.messages().send(sender, "admin-set", Map.of("{player}", name, "{titre}", title.rendering(variant, -1).still()));
            if (player != null && !player.hasPermission(title.permission())) {
                plugin.messages().send(sender, "admin-set-not-owned", Map.of("{player}", name, "{permission}", title.permission()));
            }
        });
    }

    private void clear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "admin-usage-player", Map.of("{subcommand}", "clear"));
            return;
        }
        String name = args[1];
        UUID uuid = resolve(name);
        plugin.selections().write(uuid, null).whenComplete((ignored, error) -> {
            if (error != null) {
                plugin.messages().send(sender, "admin-storage-error");
                return;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.selections().apply(player, null);
            }
            plugin.messages().send(sender, "admin-cleared", Map.of("{player}", name));
        });
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "admin-usage-player", Map.of("{subcommand}", "info"));
            return;
        }
        String name = args[1];
        UUID uuid = resolve(name);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            Selections.Worn worn = plugin.selections().worn(uuid);
            sendInfo(sender, name, worn == null ? null : new Storage.Choice(worn.titleId(), worn.variant()), online);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Storage.Choice choice = plugin.storage().load(uuid);
                Bukkit.getScheduler().runTask(plugin, () -> sendInfo(sender, name, choice, null));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Lecture du titre de " + name + " impossible", e);
                Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().send(sender, "admin-storage-error"));
            }
        });
    }

    private void sendInfo(CommandSender sender, String name, Storage.Choice choice, Player online) {
        if (choice == null) {
            plugin.messages().send(sender, "admin-info-none", Map.of("{player}", name));
            return;
        }
        Title title = plugin.titles().get(choice.titleId());
        String text;
        if (title == null) {
            text = "&c?";
        } else if (online != null) {
            Rendering rendering = plugin.selections().rendering(online.getUniqueId());
            text = rendering != null ? rendering.still() : title.rendering(choice.variant(), -1).still();
        } else {
            text = title.rendering(choice.variant(), -1).still();
        }
        boolean owned = online == null || (title != null && online.hasPermission(title.permission()));
        plugin.messages().send(sender, "admin-info", Map.of(
                "{player}", name,
                "{titre}", text,
                "{id}", choice.titleId(),
                "{variant}", choice.variant() ? ", variant" : "",
                "{owned}", owned ? "" : plugin.messages().raw("admin-info-not-owned")));
    }

    private void list(CommandSender sender) {
        plugin.messages().send(sender, "admin-list-header", Map.of("{count}", Integer.toString(plugin.titles().size())));
        for (Title title : plugin.titles().all()) {
            plugin.messages().send(sender, "admin-list-line", Map.of("{id}", title.id(), "{titre}", title.rendering().still()));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                      @NotNull String[] args) {
        List<String> usable = usable(sender);
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], usable, new ArrayList<>());
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!usable.contains(subcommand)) {
            return List.of();
        }
        if (args.length == 2 && (subcommand.equals("set") || subcommand.equals("clear") || subcommand.equals("info"))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }
        if (args.length == 3 && subcommand.equals("set")) {
            List<String> ids = new ArrayList<>();
            for (Title title : plugin.titles().all()) {
                ids.add(title.id());
            }
            return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
        }
        if (args.length == 4 && subcommand.equals("set")) {
            return StringUtil.copyPartialMatches(args[3], List.of("variant"), new ArrayList<>());
        }
        return List.of();
    }
}
