package org.Cry_Legende.sHTitres.menu;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.clip.placeholderapi.PlaceholderAPI;
import org.Cry_Legende.sHTitres.SHTitres;
import org.Cry_Legende.sHTitres.config.Messages;
import org.Cry_Legende.sHTitres.config.Settings;
import org.Cry_Legende.sHTitres.player.Selections;
import org.Cry_Legende.sHTitres.render.Colors;
import org.Cry_Legende.sHTitres.render.Rendering;
import org.Cry_Legende.sHTitres.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Menu des titres d'un joueur : une catégorie, une page. Les titres animés le sont aussi dans le menu.
 */
public final class TitleMenu implements InventoryHolder {

    private final SHTitres plugin;
    private final Player viewer;
    private final List<String> categoryOrder;
    private String category;
    private int page;
    private int pages = 1;
    private Inventory inventory;
    /** Titre affiché dans chaque case (possédé ou en vente ; pas les barrières). */
    private final Map<Integer, Title> slots = new HashMap<>();
    private final Map<Integer, Boolean> ownedSlots = new HashMap<>();
    /** Ligne d'aperçu, placeholders du joueur déjà résolus ({titre} reste à remplacer). */
    private String preview;
    private int adaptiveColor = -1;

    private TitleMenu(SHTitres plugin, Player viewer, String category, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.categoryOrder = new ArrayList<>(plugin.settings().categories.keySet());
        this.category = categoryOrder.contains(category) ? category : categoryOrder.getFirst();
        this.page = Math.max(0, page);
    }

    public static void open(SHTitres plugin, Player player, String category, int page) {
        TitleMenu menu = new TitleMenu(plugin, player, category, page);
        menu.build();
        player.openInventory(menu.inventory);
        plugin.playSound(player, plugin.settings().soundOpen);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player viewer() {
        return viewer;
    }

    private Messages messages() {
        return plugin.messages();
    }

    private Settings settings() {
        return plugin.settings();
    }

    private void build() {
        String template = messages().raw("menu.preview");
        preview = plugin.hasPlaceholderApi() ? PlaceholderAPI.setPlaceholders(viewer, template) : template;
        String placeholder = settings().adaptiveColorPlaceholder;
        if (!placeholder.isEmpty() && plugin.hasPlaceholderApi()) {
            adaptiveColor = Colors.lastColor(PlaceholderAPI.setPlaceholders(viewer, placeholder));
        }

        List<Title> visible = new ArrayList<>();
        for (Title title : plugin.titles().inCategory(category)) {
            if (!title.hidden() || viewer.hasPermission(title.permission())) {
                visible.add(title);
            }
        }
        int perPage = Math.max(1, settings().titleSlots.size());
        pages = Math.max(1, (visible.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);

        String name = categoryName(category);
        String titleKey = pages > 1 ? "menu.title-pages" : "menu.title";
        String menuTitle = Messages.apply(messages().raw(titleKey), Map.of(
                "{categorie}", name, "{page}", Integer.toString(page + 1), "{pages}", Integer.toString(pages)));
        inventory = Bukkit.createInventory(this, settings().size(), Messages.component(menuTitle));

        ItemStack border = pane(settings().borderMaterial, false);
        for (int slot : settings().borderSlots) {
            inventory.setItem(slot, border);
        }
        Settings.Category current = settings().categories.get(category);
        ItemStack accent = pane(settings().accentMaterial, current != null && current.accentGlint());
        for (int slot : settings().accentSlots) {
            inventory.setItem(slot, accent);
        }

        slots.clear();
        ownedSlots.clear();
        List<Integer> titleSlots = settings().titleSlots;
        for (int i = 0; i < titleSlots.size(); i++) {
            int index = page * perPage + i;
            int slot = titleSlots.get(i);
            if (index >= visible.size()) {
                inventory.setItem(slot, null);
                continue;
            }
            Title title = visible.get(index);
            boolean owned = viewer.hasPermission(title.permission());
            if (!owned && current != null && current.unowned() == Settings.Unowned.LOCKED) {
                inventory.setItem(slot, lockedItem());
                continue;
            }
            slots.put(slot, title);
            ownedSlots.put(slot, owned);
            inventory.setItem(slot, titleItem(title, owned, false));
        }

        inventory.setItem(settings().removeSlot, removeItem());
        if (page > 0) {
            inventory.setItem(settings().previousSlot, pageItem("menu.previous", page));
        }
        if (page < pages - 1) {
            inventory.setItem(settings().nextSlot, pageItem("menu.next", page + 2));
        }
        if (categoryOrder.size() > 1) {
            inventory.setItem(settings().switchSlot, switchItem());
        }
        if (hasBack()) {
            inventory.setItem(settings().backSlot, backItem());
        }
    }

    private String categoryName(String id) {
        String name = messages().raw("menu.categories." + id);
        return name.isEmpty() ? id : name;
    }

    private String nextCategory() {
        int index = categoryOrder.indexOf(category);
        return categoryOrder.get((index + 1) % categoryOrder.size());
    }

    /** Recalcule les titres animés de la page (appelé à chaque image d'animation). */
    public void tick() {
        for (Map.Entry<Integer, Title> entry : slots.entrySet()) {
            Title title = entry.getValue();
            if (title.animated()) {
                inventory.setItem(entry.getKey(), titleItem(title, ownedSlots.get(entry.getKey()), true));
            }
        }
    }

    private ItemStack titleItem(Title title, boolean owned, boolean animatedFrame) {
        Rendering main = title.rendering();
        String mainText = animatedFrame ? main.current() : main.still();
        Selections.Worn worn = plugin.selections().worn(viewer.getUniqueId());
        boolean wearing = owned && worn != null && worn.owned() && worn.titleId().equals(title.id());

        List<String> lore = new ArrayList<>();
        addLines(lore, "menu.lore-preview", Map.of("{apercu}", previewLine(mainText)));
        String variantLabel = variantLabel(title);
        if (title.hasVariant()) {
            Rendering variant = title.rendering(true, adaptiveColor);
            String variantText = animatedFrame ? variant.current() : variant.still();
            addLines(lore, "menu.lore-variant", Map.of("{variant}", variantLabel, "{apercu_variant}", previewLine(variantText)));
        }
        if (!title.obtain().isEmpty()) {
            for (String line : messages().rawList("menu.lore-obtain")) {
                if (line.contains("{obtention}")) {
                    lore.addAll(title.obtain());
                } else {
                    lore.add(line);
                }
            }
        }
        if (wearing) {
            addLines(lore, "menu.lore-worn", Map.of());
        }
        if (owned) {
            addLines(lore, "menu.lore-apply", Map.of());
            if (title.hasVariant()) {
                addLines(lore, "menu.lore-apply-variant", Map.of(
                        "{variant_lower}", variantLabel.toLowerCase(Locale.ROOT), "{variant}", variantLabel));
            }
        } else {
            addLines(lore, "menu.lore-buy", Map.of());
        }

        ItemStack item = plugin.icons().icon(title);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.itemText(Messages.apply(messages().raw("menu.item-name"), Map.of("{titre}", mainText))));
        meta.lore(Messages.itemLines(lore));
        meta.addItemFlags(ItemFlag.values());
        if (title.glint() || wearing) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String variantLabel(Title title) {
        if (title.variantKind() == Title.VariantKind.ADAPTIVE) {
            return messages().raw("menu.variant-adaptive");
        }
        String label = title.variantLabel();
        return label == null || label.isBlank() ? messages().raw("menu.variant-alternate") : label;
    }

    private String previewLine(String titleText) {
        return preview.replace("{titre}", titleText);
    }

    private void addLines(List<String> lore, String key, Map<String, String> placeholders) {
        for (String line : messages().rawList(key)) {
            // Placeholders PlaceholderAPI du texte de messages.yml d'abord : les valeurs insérées ensuite ne sont pas relues
            lore.add(Messages.apply(papi(line), placeholders));
        }
    }

    private String papi(String text) {
        return plugin.hasPlaceholderApi() && text.indexOf('%') >= 0 ? PlaceholderAPI.setPlaceholders(viewer, text) : text;
    }

    private ItemStack lockedItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.itemText(messages().raw("menu.locked-item.name")));
        meta.lore(Messages.itemLines(messages().rawList("menu.locked-item.lore")));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack removeItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setPlayerProfile(viewer.getPlayerProfile());
        Title shown = plugin.selections().shownTitle(viewer.getUniqueId());
        meta.displayName(Messages.itemText(messages().raw("menu.remove.name")));
        if (shown == null) {
            List<String> lore = new ArrayList<>();
            addLines(lore, "menu.remove.lore-none", Map.of());
            meta.lore(Messages.itemLines(lore));
        } else {
            Rendering rendering = plugin.selections().rendering(viewer.getUniqueId());
            String text = rendering == null ? shown.rendering().still() : rendering.still();
            List<String> lore = new ArrayList<>();
            addLines(lore, "menu.remove.lore", Map.of("{titre}", text));
            meta.lore(Messages.itemLines(lore));
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pageItem(String key, int targetPage) {
        ItemStack item = new ItemStack(settings().pageMaterial);
        ItemMeta meta = item.getItemMeta();
        Map<String, String> placeholders = Map.of("{page}", Integer.toString(targetPage), "{pages}", Integer.toString(pages));
        meta.displayName(Messages.itemText(Messages.apply(messages().raw(key + ".name"), placeholders)));
        List<String> lore = new ArrayList<>();
        addLines(lore, key + ".lore", placeholders);
        meta.lore(Messages.itemLines(lore));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** Bouton de retour : affiché quand une case et au moins une commande sont configurées. */
    private boolean hasBack() {
        Settings settings = settings();
        return settings.backSlot >= 0 && settings.backSlot < settings.size() && !settings.backCommands.isEmpty();
    }

    private ItemStack backItem() {
        ItemStack item = new ItemStack(settings().backMaterial);
        ItemMeta meta = item.getItemMeta();
        // messages.yml d'avant la 1.2 : pas de texte pour ce bouton, on prend celui des autres menus du serveur
        String name = messages().raw("menu.back.name");
        meta.displayName(Messages.itemText(name.isBlank() ? "&c⤾ Retour &7(Clic)" : name));
        List<String> lore = new ArrayList<>();
        addLines(lore, "menu.back.lore", Map.of());
        meta.lore(Messages.itemLines(lore));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack switchItem() {
        String target = categoryName(nextCategory());
        Map<String, String> placeholders = Map.of("{categorie}", target, "{categorie_lower}", target.toLowerCase(Locale.ROOT));
        ItemStack item = new ItemStack(settings().switchMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.itemText(Messages.apply(messages().raw("menu.switch.name"), placeholders)));
        List<String> lore = new ArrayList<>();
        addLines(lore, "menu.switch.lore", placeholders);
        meta.lore(Messages.itemLines(lore));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material, boolean glint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setHideTooltip(true);
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Clic dans le menu (déjà annulé). */
    public void click(int slot, ClickType click) {
        Settings settings = settings();
        Title title = slots.get(slot);
        if (title != null) {
            if (Boolean.TRUE.equals(ownedSlots.get(slot))) {
                boolean variant = click.isRightClick() && title.hasVariant();
                viewer.closeInventory();
                plugin.selections().select(viewer, title, variant).whenComplete((ignored, error) -> {
                    if (error != null) {
                        messages().send(viewer, "storage-error");
                        return;
                    }
                    Rendering rendering = title.rendering(variant, adaptiveColor);
                    messages().send(viewer, "selected", Map.of("{titre}", rendering.still(), "{nom}", title.name()));
                    plugin.playSound(viewer, settings.soundSelect);
                });
            } else {
                viewer.closeInventory();
                messages().send(viewer, "buy");
                plugin.runBuyCommands(viewer);
            }
            return;
        }
        if (slot == settings.removeSlot) {
            Title shown = plugin.selections().shownTitle(viewer.getUniqueId());
            Selections.Worn worn = plugin.selections().worn(viewer.getUniqueId());
            if (worn == null) {
                return;
            }
            Rendering rendering = plugin.selections().rendering(viewer.getUniqueId());
            String text = rendering != null ? rendering.still() : shown != null ? shown.rendering().still() : "";
            viewer.closeInventory();
            plugin.selections().clear(viewer).whenComplete((ignored, error) -> {
                if (error != null) {
                    messages().send(viewer, "storage-error");
                    return;
                }
                messages().send(viewer, "removed", Map.of("{titre}", text));
                plugin.playSound(viewer, settings.soundRemove);
            });
            return;
        }
        if (slot == settings.previousSlot && page > 0) {
            reopen(category, page - 1);
        } else if (slot == settings.nextSlot && page < pages - 1) {
            reopen(category, page + 1);
        } else if (slot == settings.switchSlot && categoryOrder.size() > 1) {
            reopen(nextCategory(), 0);
        } else if (slot == settings.backSlot && hasBack()) {
            // Au tick suivant : la commande ouvre un autre menu, ce qui ne se fait pas pendant le clic
            plugin.playSound(viewer, settings.soundPage);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline()) {
                    plugin.runBackCommands(viewer);
                }
            });
        }
    }

    private void reopen(String newCategory, int newPage) {
        category = newCategory;
        page = newPage;
        build();
        viewer.openInventory(inventory);
        plugin.playSound(viewer, settings().soundPage);
    }

    /** Icônes des titres : matériau ou tête « basehead-&lt;texture&gt; ». */
    public static final class Icons {

        private final Map<String, ItemStack> cache = new HashMap<>();

        public void clear() {
            cache.clear();
        }

        public ItemStack icon(Title title) {
            return cache.computeIfAbsent(title.id(), id -> create(title.icon())).clone();
        }

        private static ItemStack create(String spec) {
            if (spec != null && spec.startsWith("basehead-")) {
                String texture = spec.substring("basehead-".length());
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), null);
                profile.setProperty(new ProfileProperty("textures", texture));
                meta.setPlayerProfile(profile);
                head.setItemMeta(meta);
                return head;
            }
            Material material = spec == null ? null : Material.matchMaterial(spec);
            return new ItemStack(material == null || !material.isItem() ? Material.NAME_TAG : material);
        }
    }
}
