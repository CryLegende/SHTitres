package org.Cry_Legende.sHTitres;

import org.Cry_Legende.sHTitres.command.AdminCommand;
import org.Cry_Legende.sHTitres.command.TitresCommand;
import org.Cry_Legende.sHTitres.config.Messages;
import org.Cry_Legende.sHTitres.config.Settings;
import org.Cry_Legende.sHTitres.config.TitleLoader;
import org.Cry_Legende.sHTitres.menu.MenuListener;
import org.Cry_Legende.sHTitres.menu.TitleMenu;
import org.Cry_Legende.sHTitres.migration.LuckPermsMigration;
import org.Cry_Legende.sHTitres.papi.TitresExpansion;
import org.Cry_Legende.sHTitres.player.Selections;
import org.Cry_Legende.sHTitres.storage.Storage;
import org.Cry_Legende.sHTitres.title.TitleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class SHTitres extends JavaPlugin implements Listener {

    private Settings settings;
    private Messages messages;
    private TitleRegistry titles;
    private Storage storage;
    private Selections selections;
    private MenuListener menus;
    private final TitleMenu.Icons icons = new TitleMenu.Icons();
    private BukkitTask frameTask;
    private BukkitTask ownershipTask;
    private TitresExpansion expansion;

    /** Fichiers lus d'un coup : rien n'est remplacé si l'un d'eux est invalide. */
    private record Loaded(Settings settings, Messages messages, TitleLoader.Result titles) {
    }

    @Override
    public void onEnable() {
        for (String file : List.of("config.yml", "titles.yml", "messages.yml")) {
            if (!new File(getDataFolder(), file).exists()) {
                saveResource(file, false);
            }
        }
        Loaded loaded;
        try {
            loaded = loadFiles();
        } catch (LoadException e) {
            getLogger().severe(e.file + " invalide : " + e.getMessage() + ". Plugin désactivé.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        apply(loaded);

        try {
            storage = new Storage(settings.database);
        } catch (SQLException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "Base de données injoignable, plugin désactivé", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        selections = new Selections(this);
        menus = new MenuListener(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(menus, this);

        TitresCommand titresCommand = new TitresCommand(this);
        PluginCommand titres = getCommand("titres");
        if (titres != null) {
            titres.setExecutor(titresCommand);
            titres.setTabCompleter(titresCommand);
        }
        AdminCommand adminCommand = new AdminCommand(this);
        PluginCommand admin = getCommand("shtitres");
        if (admin != null) {
            admin.setExecutor(adminCommand);
            admin.setTabCompleter(adminCommand);
        }

        if (hasPlaceholderApi()) {
            expansion = new TitresExpansion(this);
            expansion.register();
        } else {
            getLogger().warning("PlaceholderAPI absent : les titres ne seront affichés nulle part.");
        }
        startTasks();

        // Joueurs déjà connectés (chargement à chaud)
        for (Player player : Bukkit.getOnlinePlayers()) {
            selections.join(player);
        }

        if (settings.migrateLuckPerms) {
            if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                new LuckPermsMigration(this).run();
            } else {
                getLogger().warning("migration.luckperms-suffix activé mais LuckPerms est absent.");
            }
        }
        getLogger().info(titles.size() + " titres chargés.");
    }

    @Override
    public void onDisable() {
        if (frameTask != null) {
            frameTask.cancel();
        }
        if (ownershipTask != null) {
            ownershipTask.cancel();
        }
        if (menus != null) {
            menus.closeAll();
        }
        if (expansion != null) {
            expansion.unregister();
        }
        if (storage != null) {
            storage.close();
        }
    }

    private void startTasks() {
        if (frameTask != null) {
            frameTask.cancel();
        }
        if (ownershipTask != null) {
            ownershipTask.cancel();
        }
        frameTask = Bukkit.getScheduler().runTaskTimer(this, menus::tick, settings.frameTicks, settings.frameTicks);
        long period = settings.ownershipCheckSeconds * 20L;
        ownershipTask = Bukkit.getScheduler().runTaskTimer(this, selections::refreshAll, period, period);
    }

    private static final class LoadException extends Exception {
        final String file;

        LoadException(String file, String message) {
            super(message);
            this.file = file;
        }
    }

    private Loaded loadFiles() throws LoadException {
        YamlConfiguration config = strict("config.yml");
        Settings newSettings = new Settings(config, getLogger());

        YamlConfiguration messagesYaml = strict("messages.yml");
        InputStream bundled = getResource("messages.yml");
        if (bundled != null) {
            messagesYaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
        Messages newMessages = new Messages(messagesYaml);

        YamlConfiguration titlesYaml = strict("titles.yml");
        long frameMillis = newSettings.frameTicks * 50L;
        TitleLoader.Result result = TitleLoader.read(titlesYaml, newSettings.categories.keySet(), frameMillis);
        if (result.registry().size() == 0) {
            throw new LoadException("titles.yml", "aucun titre valide");
        }
        return new Loaded(newSettings, newMessages, result);
    }

    private YamlConfiguration strict(String name) throws LoadException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(new File(getDataFolder(), name));
        } catch (IOException | InvalidConfigurationException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().lines().findFirst().orElse("");
            throw new LoadException(name, message);
        }
        return yaml;
    }

    private void apply(Loaded loaded) {
        this.settings = loaded.settings();
        this.messages = loaded.messages();
        this.titles = loaded.titles().registry();
        icons.clear();
        for (String warning : loaded.titles().warnings()) {
            getLogger().warning(warning);
        }
    }

    /** /shtitres reload. */
    public void reload(CommandSender sender) {
        Loaded loaded;
        try {
            loaded = loadFiles();
        } catch (LoadException e) {
            messages.send(sender, "admin-reload-failed", Map.of("{file}", e.file, "{error}", e.getMessage()));
            return;
        }
        int oldFrameTicks = settings.frameTicks;
        int oldOwnership = settings.ownershipCheckSeconds;
        menus.closeAll();
        apply(loaded);
        if (oldFrameTicks != settings.frameTicks || oldOwnership != settings.ownershipCheckSeconds) {
            startTasks();
        }
        selections.refreshAll();
        messages.send(sender, "admin-reload", Map.of("{count}", Integer.toString(titles.size())));
        if (!loaded.titles().warnings().isEmpty()) {
            messages.send(sender, "admin-reload-warnings", Map.of("{count}", Integer.toString(loaded.titles().warnings().size())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            selections.preload(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        selections.join(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        selections.quit(event.getPlayer().getUniqueId());
    }

    public void playSound(Player player, String key) {
        if (key != null && !key.isBlank()) {
            player.playSound(player.getLocation(), key, SoundCategory.MASTER, 1f, 1f);
        }
    }

    public void runBuyCommands(Player player) {
        runCommands(player, settings.buyCommands);
    }

    /** Commandes du bouton de retour du menu (menu.back-commands). */
    public void runBackCommands(Player player) {
        runCommands(player, settings.backCommands);
    }

    private void runCommands(Player player, List<String> lines) {
        for (String line : lines) {
            String command = line.replace("{player}", player.getName());
            if (command.startsWith("player:")) {
                player.performCommand(command.substring("player:".length()).trim());
            } else if (command.startsWith("console:")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.substring("console:".length()).trim());
            }
        }
    }

    public boolean hasPlaceholderApi() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public TitleRegistry titles() {
        return titles;
    }

    public Storage storage() {
        return storage;
    }

    public Selections selections() {
        return selections;
    }

    public TitleMenu.Icons icons() {
        return icons;
    }
}
