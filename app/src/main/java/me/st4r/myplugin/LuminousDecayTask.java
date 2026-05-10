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
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
       
            checkAndCleanup(player, player.getInventory().getItemInMainHand(), now);
            checkAndCleanup(player, player.getInventory().getItemInOffHand(), now);
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                checkAndCleanup(player, armor, now);
            }
        }
    }

    private void checkAndCleanup(Player player, ItemStack item, long now) {
        if (item == null || !item.hasItemMeta()) return;
        
        var meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        
 
        if (!pdc.has(DVPlus.LUMINOUS_KEY, PersistentDataType.LONG)) return;

        long expiry = pdc.get(DVPlus.LUMINOUS_KEY, PersistentDataType.LONG);
        long timeLeftMillis = expiry - now;

        if (timeLeftMillis <= 0) {
            pdc.remove(DVPlus.LUMINOUS_KEY);
            if (meta.hasLore()) {
                var lore = meta.getLore();
                lore.removeIf(line -> line.contains("Luminous"));
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
            player.sendMessage(ChatColor.GRAY + "Your equipment's glow has faded.");
            return;
        }

        long secondsLeft = timeLeftMillis / 1000;
        if (secondsLeft > 0 && secondsLeft % 300 == 0) { // 300 seconds = 5 minutes
            player.sendMessage(ChatColor.AQUA + "✦ Your equipment has " + (secondsLeft / 60) + " minutes of light remaining.");
        }
        

    }
}