package org.Cry_Legende.sHTitres;

import org.Cry_Legende.sHTitres.config.TitleLoader;
import org.Cry_Legende.sHTitres.migration.LuckPermsMigration;
import org.Cry_Legende.sHTitres.render.Rendering;
import org.Cry_Legende.sHTitres.storage.Storage;
import org.Cry_Legende.sHTitres.title.Title;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Hors serveur : rend tous les titres de titles.yml en JSON (aperçu), et teste la table de reprise des suffixes
 * LuckPerms sur une liste de valeurs (une par ligne).
 * <p>
 * java ... Export &lt;titles.yml&gt; &lt;sortie.json&gt; [suffixes.txt luckperms-migration.yml]
 */
public final class Export {

    public static void main(String[] args) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(Path.of(args[0]).toFile());
        TitleLoader.Result result = TitleLoader.read(yaml, Set.of("titres", "speciaux"), 100);
        result.warnings().forEach(warning -> System.err.println("AVERTISSEMENT " + warning));
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        for (Title title : result.registry().all()) {
            if (!first) {
                json.append(",\n");
            }
            first = false;
            json.append("{\"id\":").append(quote(title.id()))
                    .append(",\"category\":").append(quote(title.category()))
                    .append(",\"name\":").append(quote(title.name()))
                    .append(",\"hidden\":").append(title.hidden())
                    .append(",\"variantKind\":").append(quote(title.variantKind().name()))
                    .append(",\"variantLabel\":").append(quote(title.variantLabel() == null ? "" : title.variantLabel()))
                    .append(",\"obtain\":").append(array(title.obtain()))
                    .append(",\"main\":").append(rendering(title.rendering()));
            if (title.hasVariant()) {
                json.append(",\"variant\":").append(rendering(title.rendering(true, 0x55FF55)));
            }
            json.append('}');
        }
        json.append("\n]\n");
        Files.writeString(Path.of(args[1]), json, StandardCharsets.UTF_8);
        System.out.println(result.registry().size() + " titres rendus");

        if (args.length >= 4) {
            List<LuckPermsMigration.Entry> table;
            try (InputStream stream = new FileInputStream(args[3])) {
                table = LuckPermsMigration.readTable(stream);
            }
            int matched = 0;
            int total = 0;
            for (String line : Files.readAllLines(Path.of(args[2]), StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t");
                String value = parts[0];
                total++;
                Storage.Choice choice = LuckPermsMigration.match(table, value);
                if (choice == null) {
                    System.out.println("NON RECONNU  " + line);
                } else {
                    matched++;
                    System.out.println("ok " + choice.titleId() + (choice.variant() ? " (variante)" : "") + "  <=  " + line);
                }
            }
            System.out.println(matched + "/" + total + " suffixes reconnus");
        }
    }

    private static String rendering(Rendering rendering) {
        return "{\"still\":" + quote(rendering.still()) + ",\"frameMillis\":" + rendering.frameMillis()
                + ",\"frames\":" + array(List.of(rendering.frames())) + "}";
    }

    private static String array(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(quote(values.get(i)));
        }
        return builder.append(']').toString();
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }
}
