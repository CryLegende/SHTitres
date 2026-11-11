package org.Cry_Legende.sHTitres.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** config.yml. */
public final class Settings {

    public enum Unowned {
        BUY,
        LOCKED
    }

    public record Category(String id, Unowned unowned, boolean accentGlint) {
    }

    public record Database(String host, int port, String name, String username, String password, int poolSize) {
    }

    public final Database database;
    public final String adaptiveColorPlaceholder;
    public final int frameTicks;
    public final int ownershipCheckSeconds;
    public final boolean migrateLuckPerms;
    /** Catégories dans l'ordre du fichier. */
    public final Map<String, Category> categories;
    public final List<String> buyCommands;

    public final int rows;
    public final List<Integer> titleSlots;
    public final Material borderMaterial;
    public final List<Integer> borderSlots;
    public final Material accentMaterial;
    public final List<Integer> accentSlots;
    public final int previousSlot;
    public final int removeSlot;
    public final int nextSlot;
    public final int switchSlot;
    public final Material switchMaterial;
    public final Material pageMaterial;
    public final String soundOpen;
    public final String soundSelect;
    public final String soundRemove;
    public final String soundPage;

    public Settings(YamlConfiguration yaml, Logger logger) {
        ConfigurationSection db = yaml.getConfigurationSection("database");
        if (db == null) {
            db = yaml.createSection("database");
        }
        this.database = new Database(db.getString("host", "127.0.0.1"), db.getInt("port", 3306),
                db.getString("database", "Cosmetiques"), db.getString("username", "root"),
                db.getString("password", ""), Math.max(1, db.getInt("pool-size", 4)));
        this.adaptiveColorPlaceholder = yaml.getString("adaptive-color-placeholder", "");
        this.frameTicks = Math.max(1, yaml.getInt("animation.frame-ticks", 2));
        this.ownershipCheckSeconds = Math.max(5, yaml.getInt("ownership-check-seconds", 60));
        this.migrateLuckPerms = yaml.getBoolean("migration.luckperms-suffix", false);

        Map<String, Category> map = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("categories");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String unowned = section.getString(id + ".unowned", "buy");
                Unowned mode = "locked".equalsIgnoreCase(unowned) ? Unowned.LOCKED : Unowned.BUY;
                map.put(id, new Category(id, mode, section.getBoolean(id + ".accent-glint", false)));
            }
        }
        if (map.isEmpty()) {
            map.put("titres", new Category("titres", Unowned.BUY, false));
        }
        this.categories = map;
        this.buyCommands = yaml.getStringList("buy-commands");

        this.rows = Math.clamp(yaml.getInt("menu.rows", 5), 1, 6);
        this.titleSlots = slots(yaml.getIntegerList("menu.title-slots"));
        this.borderMaterial = material(yaml.getString("menu.border.material"), Material.BLACK_STAINED_GLASS_PANE, logger);
        this.borderSlots = slots(yaml.getIntegerList("menu.border.slots"));
        this.accentMaterial = material(yaml.getString("menu.accent.material"), Material.PURPLE_STAINED_GLASS_PANE, logger);
        this.accentSlots = slots(yaml.getIntegerList("menu.accent.slots"));
        this.previousSlot = yaml.getInt("menu.buttons.previous", 39);
        this.removeSlot = yaml.getInt("menu.buttons.remove", 40);
        this.nextSlot = yaml.getInt("menu.buttons.next", 41);
        this.switchSlot = yaml.getInt("menu.buttons.switch", 44);
        this.switchMaterial = material(yaml.getString("menu.switch-material"), Material.END_CRYSTAL, logger);
        this.pageMaterial = material(yaml.getString("menu.page-material"), Material.ARROW, logger);
        this.soundOpen = yaml.getString("menu.sounds.open", "");
        this.soundSelect = yaml.getString("menu.sounds.select", "");
        this.soundRemove = yaml.getString("menu.sounds.remove", "");
        this.soundPage = yaml.getString("menu.sounds.page", "");
    }

    public int size() {
        return rows * 9;
    }

    private List<Integer> slots(List<Integer> raw) {
        List<Integer> out = new ArrayList<>();
        for (Integer slot : raw) {
            if (slot != null && slot >= 0 && slot < rows * 9 && !out.contains(slot)) {
                out.add(slot);
            }
        }
        return out;
    }

    private static Material material(String name, Material fallback, Logger logger) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            logger.warning("config.yml : matériau « " + name + " » inconnu, " + fallback.name() + " retenu");
            return fallback;
        }
        return material;
    }
}
