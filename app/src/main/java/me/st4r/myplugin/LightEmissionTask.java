/*
 * DVPlus (Dams's Vanilla+)
 * Copyright (C) 2026 fxllingstar
 *
 * Licensed under the GNU Affero General Public License v3.0.
 * If you run a modified version of this software as a service, 
 * you must provide access to the source code of your modifications.
 *
 * Read the License file here: 
 * https://github.com/fxllingstar/Dams-s-Vanilla-plus/blob/main/LICENSE
 */



package me.st4r.myplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LightEmissionTask extends BukkitRunnable {
    
    private final DVPlus plugin;
    private final Map<UUID, Location> lastLightLocations = new HashMap<>();
    
    public LightEmissionTask(DVPlus plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            
            if (hasLuminousItem(player)) {
                Location currentLoc = player.getLocation().add(0, 1, 0).getBlock().getLocation();
                Location lastLoc = lastLightLocations.get(uuid);
                
                if (lastLoc == null || !lastLoc.equals(currentLoc)) {
                    if (lastLoc != null) {
                        player.sendBlockChange(lastLoc, lastLoc.getBlock().getBlockData());
                    }
                    
                    Block currentBlock = currentLoc.getBlock();
                    if (currentBlock.isPassable() && !currentBlock.isLiquid()) {
                        player.sendBlockChange(currentLoc, Bukkit.createBlockData("minecraft:light[level=13]"));
                        lastLightLocations.put(uuid, currentLoc);
                    } else {
                        lastLightLocations.remove(uuid);
                    }
                }
            } else {
                Location lastLoc = lastLightLocations.remove(uuid);
                if (lastLoc != null) {
                    player.sendBlockChange(lastLoc, lastLoc.getBlock().getBlockData());
                }
            }
        }
    }
    
    private boolean hasLuminousItem(Player player) {
        // Check Main Hand
        if (isLuminous(player.getInventory().getItemInMainHand())) return true;
        
        // Check Off-Hand
        if (isLuminous(player.getInventory().getItemInOffHand())) return true;
        
        // Check Armor
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (isLuminous(armor)) return true;
        }
        
        return false;
    }
    
    private boolean isLuminous(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(DVPlus.LUMINOUS_KEY, PersistentDataType.LONG);
    }
}