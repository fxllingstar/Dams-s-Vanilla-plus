package me.st4r.myplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.UUID;

public class LuminousItemListener implements Listener {
    
    private final JavaPlugin plugin;
    private final HashMap<UUID, BukkitRunnable> activeDecayTasks = new HashMap<>();
    
    public LuminousItemListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        
        // Cancel old decay task
        if (activeDecayTasks.containsKey(player.getUniqueId())) {
            activeDecayTasks.get(player.getUniqueId()).cancel();
            activeDecayTasks.remove(player.getUniqueId());
        }
        
        // Start new decay task if item is luminous
        if (newItem != null && isLuminous(newItem)) {
            BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    ItemStack current = player.getInventory().getItemInMainHand();
                    if (current == null || !isLuminous(current)) {
                        cancel();
                        return;
                    }
                    
                    decreaseLuminousTime(current, player);
                }
            };
            
            task.runTaskTimer(plugin, 20L, 20L); // Every second
            activeDecayTasks.put(player.getUniqueId(), task);
        }
    }
    
    private boolean isLuminous(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(DVPlus.LUMINOUS_KEY, PersistentDataType.INTEGER);
    }

    private void decreaseLuminousTime(ItemStack item, Player player) {
    if (!item.hasItemMeta()) return;
    
    var meta = item.getItemMeta();
    var pdc = meta.getPersistentDataContainer();
    
    int remaining = pdc.getOrDefault(DVPlus.LUMINOUS_KEY, PersistentDataType.INTEGER, 0);
    
    if (remaining <= 0) {
        // Remove luminous effect
        pdc.remove(DVPlus.LUMINOUS_KEY);
        
        // Remove lore
        if (meta.hasLore()) {
            var lore = meta.getLore();
            lore.removeIf(line -> line.contains("Luminous"));
            meta.setLore(lore);
        }
        
        // Apply changes back to item
        item.setItemMeta(meta);
        
        player.sendMessage(ChatColor.GRAY + "The luminous glow has faded...");
        
        // Cancel task
        if (activeDecayTasks.containsKey(player.getUniqueId())) {
            activeDecayTasks.get(player.getUniqueId()).cancel();
            activeDecayTasks.remove(player.getUniqueId());
        }
        return;
    }
    
    // Decrease by 1 second (20 ticks)
    pdc.set(DVPlus.LUMINOUS_KEY, PersistentDataType.INTEGER, remaining - 20);
    
    // Apply changes back to item
    item.setItemMeta(meta);
}
}