package me.st4r.myplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class LightEmissionTask extends BukkitRunnable {
    
    private final DVPlus plugin;
    
    public LightEmissionTask(DVPlus plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hasLuminousItem(player)) {
                Location loc = player.getLocation();
                loc.getBlock().setBlockData(Bukkit.createBlockData("minecraft:light[level=9]"));
         
            }
        }
    }
    
    private boolean hasLuminousItem(Player player) {
       
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isLuminous(mainHand)) return true;
        

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (isLuminous(armor)) return true;
        }
        
        return false;
    }

    
    private boolean isLuminous(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(DVPlus.LUMINOUS_KEY, PersistentDataType.INTEGER);
    }
}
