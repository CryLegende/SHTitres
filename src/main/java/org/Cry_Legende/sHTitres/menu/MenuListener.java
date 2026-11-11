package org.Cry_Legende.sHTitres.menu;

import org.Cry_Legende.sHTitres.SHTitres;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Clics des menus de titres, et animation des menus ouverts. */
public final class MenuListener implements Listener {

    private final SHTitres plugin;
    private final Set<TitleMenu> open = ConcurrentHashMap.newKeySet();

    public MenuListener(SHTitres plugin) {
        this.plugin = plugin;
    }

    /** Une image d'animation : appelé toutes les animation.frame-ticks. */
    public void tick() {
        for (TitleMenu menu : open) {
            if (menu.viewer().isOnline() && menu.viewer().getOpenInventory().getTopInventory().getHolder(false) == menu) {
                menu.tick();
            } else {
                open.remove(menu);
            }
        }
    }

    public void closeAll() {
        for (TitleMenu menu : open) {
            menu.viewer().closeInventory();
        }
        open.clear();
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof TitleMenu menu) {
            open.add(menu);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof TitleMenu menu) {
            open.remove(menu);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof TitleMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        var click = event.getClick();
        // Ouvrir ou fermer un inventaire pendant l'événement de clic est déconseillé : tick suivant
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (menu.viewer().getOpenInventory().getTopInventory().getHolder(false) == menu) {
                menu.click(slot, click);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof TitleMenu) {
            event.setCancelled(true);
        }
    }
}
