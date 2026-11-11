package org.Cry_Legende.sHTitres.migration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.types.SuffixNode;
import org.Cry_Legende.sHTitres.SHTitres;
import org.Cry_Legende.sHTitres.render.Colors;
import org.Cry_Legende.sHTitres.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Reprise des titres posés par les anciens menus DeluxeMenus en suffixe LuckPerms (meta setsuffix). La table de
 * correspondance est luckperms-migration.yml, dans le jar.
 */
public final class LuckPermsMigration {

    private static final Pattern COLOR_CODES = Pattern.compile("(?i)&#[0-9a-f]{6}|&x(&[0-9a-f]){6}|&[0-9a-fk-or]");

    /** Ancien suffixe reconnu. */
    public record Entry(String text, String titleId, String defaultColor, boolean variant, String requires) {
    }

    private final SHTitres plugin;
    private final List<Entry> entries = new ArrayList<>();

    public LuckPermsMigration(SHTitres plugin) {
        this.plugin = plugin;
        InputStream stream = plugin.getResource("luckperms-migration.yml");
        if (stream != null) {
            entries.addAll(readTable(stream));
        }
    }

    /** Lit la table de correspondance ; les entrées avec « requires » passent en premier. */
    public static List<Entry> readTable(InputStream stream) {
        List<Entry> list = new ArrayList<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        for (Map<?, ?> map : yaml.getMapList("suffixes")) {
            String text = String.valueOf(map.get("text"));
            String title = String.valueOf(map.get("title"));
            Object color = map.get("color");
            Object variant = map.get("variant");
            Object requires = map.get("requires");
            list.add(new Entry(text, title, color == null ? null : String.valueOf(color).toLowerCase(Locale.ROOT),
                    Boolean.TRUE.equals(variant), requires == null ? null : String.valueOf(requires).toLowerCase(Locale.ROOT)));
        }
        // Les entrées avec « requires » départagent des textes identiques : à tester d'abord
        list.sort((a, b) -> Boolean.compare(b.requires != null, a.requires != null));
        return list;
    }

    /** Lance la reprise en arrière-plan. */
    public void run() {
        LuckPerms luckPerms = LuckPermsProvider.get();
        luckPerms.getUserManager().searchAll(NodeMatcher.type(NodeType.SUFFIX)).whenComplete((found, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Reprise des suffixes LuckPerms : lecture impossible", error);
                return;
            }
            try {
                migrate(luckPerms, found);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Reprise des suffixes LuckPerms interrompue", e);
            }
        });
    }

    private void migrate(LuckPerms luckPerms, Map<UUID, Collection<SuffixNode>> found) throws Exception {
        Map<UUID, Storage.Choice> choices = new LinkedHashMap<>();
        Map<UUID, List<SuffixNode>> toRemove = new HashMap<>();
        Map<String, Integer> unknown = new TreeMap<>();
        for (Map.Entry<UUID, Collection<SuffixNode>> user : found.entrySet()) {
            SuffixNode best = null;
            Storage.Choice bestChoice = null;
            for (SuffixNode node : user.getValue()) {
                Storage.Choice choice = match(node.getMetaValue());
                if (choice == null) {
                    unknown.merge(node.getMetaValue(), 1, Integer::sum);
                    continue;
                }
                toRemove.computeIfAbsent(user.getKey(), uuid -> new ArrayList<>()).add(node);
                if (best == null || node.getPriority() > best.getPriority()) {
                    best = node;
                    bestChoice = choice;
                }
            }
            if (bestChoice != null) {
                choices.put(user.getKey(), bestChoice);
            }
        }
        if (choices.isEmpty()) {
            plugin.getLogger().info("Reprise des suffixes LuckPerms : aucun ancien titre à reprendre.");
            return;
        }
        int inserted = plugin.storage().insertMissing(choices);
        plugin.getLogger().info("Reprise des suffixes LuckPerms : " + choices.size() + " joueurs reconnus, "
                + inserted + " titres ajoutés (les autres avaient déjà un titre SHTitres).");
        if (!unknown.isEmpty()) {
            plugin.getLogger().info("Suffixes non reconnus, laissés dans LuckPerms : " + unknown);
        }

        // Les joueurs connectés reçoivent leur titre tout de suite
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (choices.containsKey(player.getUniqueId()) && plugin.selections().worn(player.getUniqueId()) == null) {
                    plugin.selections().join(player);
                }
            }
        });

        // Retrait des suffixes repris, par lots pour ne pas noyer LuckPerms
        List<Map.Entry<UUID, List<SuffixNode>>> pending = new ArrayList<>(toRemove.entrySet());
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        int batch = 25;
        for (int start = 0; start < pending.size(); start += batch) {
            List<Map.Entry<UUID, List<SuffixNode>>> slice = pending.subList(start, Math.min(pending.size(), start + batch));
            chain = chain.thenCompose(ignored -> {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (Map.Entry<UUID, List<SuffixNode>> entry : slice) {
                    futures.add(luckPerms.getUserManager().modifyUser(entry.getKey(), user -> {
                        for (SuffixNode node : entry.getValue()) {
                            user.data().remove(node);
                        }
                    }));
                }
                return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
            });
        }
        chain.whenComplete((ignored, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Reprise des suffixes LuckPerms : retrait incomplet", error);
            } else {
                plugin.getLogger().info("Reprise des suffixes LuckPerms : suffixes retirés chez " + pending.size() + " joueurs.");
            }
        });
    }

    private Storage.Choice match(String suffix) {
        return match(entries, suffix);
    }

    /** Titre d'un ancien suffixe, null s'il n'est pas reconnu. */
    public static Storage.Choice match(List<Entry> entries, String suffix) {
        if (suffix == null) {
            return null;
        }
        String raw = suffix.replace('§', '&');
        String key = normalize(raw);
        String lower = raw.toLowerCase(Locale.ROOT);
        for (Entry entry : entries) {
            if (!entry.text.equals(key) || (entry.requires != null && !lower.contains(entry.requires))) {
                continue;
            }
            boolean variant = entry.variant;
            if (!variant && entry.defaultColor != null) {
                String color = leadingColor(raw);
                variant = color != null && !color.equals(entry.defaultColor);
            }
            return new Storage.Choice(entry.titleId, variant);
        }
        return null;
    }

    /** Texte sans codes couleur ni espaces ; les caractères perdus par la base (émojis) deviennent « ? ». */
    static String normalize(String raw) {
        String text = COLOR_CODES.matcher(raw).replaceAll("");
        StringBuilder builder = new StringBuilder();
        text.codePoints().forEach(codePoint -> {
            if (Character.isWhitespace(codePoint)) {
                return;
            }
            if (Character.isSupplementaryCodePoint(codePoint)) {
                builder.append('?');
            } else {
                builder.appendCodePoint(codePoint);
            }
        });
        return builder.toString();
    }

    /** Dernière couleur avant le premier caractère visible : « 7 », « a », « #00a3ff »... */
    static String leadingColor(String raw) {
        String color = null;
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length()) {
                char code = Character.toLowerCase(raw.charAt(i + 1));
                if (code == '#' && i + 8 <= raw.length() && Colors.parseHex(raw.substring(i + 2, i + 8)) >= 0) {
                    color = "#" + raw.substring(i + 2, i + 8).toLowerCase(Locale.ROOT);
                    i += 8;
                    continue;
                }
                if (Character.digit(code, 16) >= 0) {
                    color = String.valueOf(code);
                    i += 2;
                    continue;
                }
                if ("klmnor".indexOf(code) >= 0) {
                    i += 2;
                    continue;
                }
            }
            if (!Character.isWhitespace(c)) {
                return color;
            }
            i++;
        }
        return color;
    }
}
