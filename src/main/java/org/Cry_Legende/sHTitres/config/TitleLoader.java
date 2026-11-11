package org.Cry_Legende.sHTitres.config;

import org.Cry_Legende.sHTitres.render.Animation;
import org.Cry_Legende.sHTitres.render.Colors;
import org.Cry_Legende.sHTitres.render.TitleStyle;
import org.Cry_Legende.sHTitres.title.Title;
import org.Cry_Legende.sHTitres.title.TitleRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lecture de titles.yml. */
public final class TitleLoader {

    /** Titres lus et problèmes rencontrés (titre ignoré, valeur corrigée). */
    public record Result(TitleRegistry registry, List<String> warnings) {
    }

    private TitleLoader() {
    }

    /**
     * @param categories catégories connues (config.yml)
     * @throws IOException                   fichier illisible
     * @throws InvalidConfigurationException YAML invalide
     */
    public static Result load(File file, Set<String> categories, long frameMillis)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return read(yaml, categories, frameMillis);
    }

    public static Result read(YamlConfiguration yaml, Set<String> categories, long frameMillis) {
        List<String> warnings = new ArrayList<>();
        List<Title> titles = new ArrayList<>();
        ConfigurationSection borders = yaml.getConfigurationSection("borders");
        ConfigurationSection obtainPresets = yaml.getConfigurationSection("obtain-presets");
        ConfigurationSection section = yaml.getConfigurationSection("titles");
        if (section == null) {
            warnings.add("titles.yml : aucune section titles");
            return new Result(new TitleRegistry(titles), warnings);
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String id = key.toLowerCase(Locale.ROOT);
            try {
                titles.add(readTitle(id, entry, borders, obtainPresets, categories, frameMillis, warnings));
            } catch (IllegalArgumentException e) {
                warnings.add("titre " + id + " ignoré : " + e.getMessage());
            }
        }
        return new Result(new TitleRegistry(titles), warnings);
    }

    private static Title readTitle(String id, ConfigurationSection entry, ConfigurationSection borders,
                                   ConfigurationSection obtainPresets, Set<String> categories, long frameMillis,
                                   List<String> warnings) {
        String category = entry.getString("category", "");
        if (!categories.contains(category)) {
            throw new IllegalArgumentException("catégorie inconnue « " + category + " »");
        }
        String permission = entry.getString("permission", "");
        if (permission.isBlank()) {
            throw new IllegalArgumentException("permission manquante");
        }
        TitleStyle style = readStyle(entry, null, borders, id, warnings);

        Title.VariantKind kind = Title.VariantKind.NONE;
        String label = null;
        TitleStyle alternate = null;
        if (entry.isConfigurationSection("variant")) {
            ConfigurationSection variant = entry.getConfigurationSection("variant");
            kind = Title.VariantKind.ALTERNATE;
            label = variant.getString("label");
            alternate = readStyle(variant, entry, borders, id + ".variant", warnings);
        } else if (entry.isString("variant")) {
            String value = entry.getString("variant", "").toLowerCase(Locale.ROOT);
            if (value.equals("adaptive")) {
                kind = Title.VariantKind.ADAPTIVE;
            } else if (!value.equals("none")) {
                warnings.add("titre " + id + " : variant « " + value + " » inconnu (adaptive ou une section)");
            }
        }

        List<String> obtain;
        if (entry.isList("obtain")) {
            obtain = entry.getStringList("obtain");
        } else if (entry.isString("obtain")) {
            String preset = entry.getString("obtain");
            obtain = obtainPresets == null ? List.of() : obtainPresets.getStringList(preset);
            if (obtain.isEmpty()) {
                warnings.add("titre " + id + " : obtain-presets." + preset + " introuvable");
            }
        } else {
            obtain = List.of();
        }

        return new Title(id, category, permission, entry.getBoolean("hidden", false),
                entry.getString("icon", "NAME_TAG"), entry.getBoolean("glint", false), obtain, style, kind, label,
                alternate, frameMillis);
    }

    /**
     * Bords, texte et animation d'une section. Une valeur absente est reprise de fallback (le titre principal
     * pour une variante).
     */
    private static TitleStyle readStyle(ConfigurationSection section, ConfigurationSection fallback,
                                        ConfigurationSection borders, String where, List<String> warnings) {
        String text = pick(section, fallback, "text");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("texte manquant (" + where + ")");
        }
        String left = pick(section, fallback, "left");
        String right = pick(section, fallback, "right");
        String border = pick(section, fallback, "border");
        if (border != null && (left == null || right == null)) {
            ConfigurationSection preset = borders == null ? null : borders.getConfigurationSection(border);
            if (preset == null) {
                warnings.add(where + " : borders." + border + " introuvable, titre sans bords");
            } else {
                if (left == null) {
                    left = preset.getString("left", "");
                }
                if (right == null) {
                    right = preset.getString("right", "");
                }
            }
        }
        ConfigurationSection animationSection = section.getConfigurationSection("animation");
        if (animationSection == null && fallback != null) {
            animationSection = fallback.getConfigurationSection("animation");
        }
        Animation animation = readAnimation(animationSection, where, warnings);
        return TitleStyle.of(left == null ? "" : left, text, right == null ? "" : right, animation);
    }

    private static String pick(ConfigurationSection section, ConfigurationSection fallback, String key) {
        if (section.isString(key)) {
            return section.getString(key);
        }
        return fallback != null && fallback.isString(key) ? fallback.getString(key) : null;
    }

    private static Animation readAnimation(ConfigurationSection section, String where, List<String> warnings) {
        if (section == null) {
            return null;
        }
        Animation.Type type = Animation.Type.parse(section.getString("type"));
        if (type == null) {
            warnings.add(where + " : animation.type « " + section.getString("type") + " » inconnu (wave, shine, pulse)");
            return null;
        }
        double seconds = section.getDouble("seconds", 3.0);
        if (seconds < 0.2) {
            warnings.add(where + " : animation.seconds trop court, 0.2 retenu");
            seconds = 0.2;
        }
        int color = Colors.parseHex(section.getString("color", "#FFFFFF"));
        if (color < 0) {
            warnings.add(where + " : animation.color illisible, #FFFFFF retenu");
            color = 0xFFFFFF;
        }
        double strength = Math.clamp(section.getDouble("strength", 0.8), 0.0, 1.0);
        return new Animation(type, seconds, color, strength);
    }
}
