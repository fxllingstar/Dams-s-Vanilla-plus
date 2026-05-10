package me.st4r.myplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class LuminousDecayTask extends BukkitRunnable {

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Check and decay Main Hand
            checkAndDecay(player, player.getInventory().getItemInMainHand());
            
            // Check and decay Off-Hand
            checkAndDecay(player, player.getInventory().getItemInOffHand());
            
            // Check and decay Armor
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                checkAndDecay(player, armor);
            }
        }
    }

    private void checkAndDecay(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        
        var meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        
        if (!pdc.has(DVPlus.LUMINOUS_KEY, PersistentDataType.INTEGER)) return;

        int remaining = pdc.get(DVPlus.LUMINOUS_KEY, PersistentDataType.INTEGER);
        
        if (remaining <= 0) {
            pdc.remove(DVPlus.LUMINOUS_KEY);
            
            if (meta.hasLore()) {
                var lore = meta.getLore();
                lore.removeIf(line -> line.contains("Luminous"));
                meta.setLore(lore);
            }
            
            item.setItemMeta(meta);
            player.sendMessage(ChatColor.GRAY + "A luminous glow has faded from your equipment...");
            return;
        }
        
    
        int newRemaining = remaining - 20;
        pdc.set(DVPlus.LUMINOUS_KEY, PersistentDataType.INTEGER, newRemaining);
        
        // Notify player every 5 minutes (6000 ticks)
        if (newRemaining > 0 && newRemaining % 6000 == 0) {
            int minutesLeft = newRemaining / 1200;
            player.sendMessage(ChatColor.AQUA + "✦ " + minutesLeft + " minutes of luminosity left on your equipment...");
        }
        
        item.setItemMeta(meta);
    }
}