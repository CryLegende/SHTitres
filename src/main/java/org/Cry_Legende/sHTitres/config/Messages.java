package org.Cry_Legende.sHTitres.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * messages.yml. Les clés absentes du fichier du serveur reprennent le texte du jar.
 */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private final YamlConfiguration yaml;

    public Messages(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    /** Texte brut d'une clé, en codes &amp; ; chaîne vide si absent. */
    public String raw(String key) {
        String value = yaml.getString(key);
        return value == null ? "" : value;
    }

    public List<String> rawList(String key) {
        return yaml.getStringList(key);
    }

    public static String apply(String text, Map<String, String> placeholders) {
        String out = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        return out;
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String text = raw(key);
        if (text.isEmpty()) {
            return;
        }
        sender.sendMessage(component(apply(text, placeholders)));
    }

    /** Codes &amp; et &amp;#RRGGBB (et §) vers un composant. */
    public static Component component(String legacy) {
        return LEGACY.deserialize(legacy.replace('§', '&'));
    }

    /** Composant d'un nom ou d'une ligne d'objet : sans l'italique que Minecraft y met par défaut. */
    public static Component itemText(String legacy) {
        return component(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> itemLines(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(itemText(line));
        }
        return out;
    }
}
