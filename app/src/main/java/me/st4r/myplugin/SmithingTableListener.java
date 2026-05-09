package me.st4r.myplugin;



import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.ChatColor;


import java.util.ArrayList;
import java.util.List;

public class SmithingTableListener implements Listener{

  private static final NamespacedKey LUMINOUS_KEY = new NamespacedKey(DVPlus.getPlugin(DVPlus.class), "luminous_time");
  private static final int MAX_LUMINOUS_TIME = 12000;


  @EventHandler
  public void onSmithing(PrepareSmithingEvent event){

    SmithingInventory inv = event.getInventory();

    ItemStack base = inv.getItem(0); //tools and armor
    ItemStack addition = inv.getItem(1);

    if (base == null || addition == null) return;
    if (addition.getType() != Material.GLOW_INK_SAC) return;
    if (!isValidLuminousItem(base.getType())) return;
   
    ItemStack result = base.clone();
    var meta = result.getItemMeta();
    if (meta == null) return;

    meta.getPersistentDataContainer().set(LUMINOUS_KEY, PersistentDataType.INTEGER, MAX_LUMINOUS_TIME);

    List<String> lore = meta.hasLore()
        ? new ArrayList<>(meta.getLore())
        : new ArrayList<>();

    lore.removeIf(line -> line.contains("Luminous"));

    lore.add(ChatColor.AQUA + "✦ Luminous");

    meta.setLore(lore);
    result.setItemMeta(meta);
    

    event.setResult(result);

  }

  private boolean isValidLuminousItem(Material type){
    return
    type.toString().endsWith("_HELMET") ||
    type.toString().endsWith("_CHESTPLATE") ||
    type.toString().endsWith("_LEGGINGS") ||
    type.toString().endsWith("_BOOTS") || 
    type.toString().endsWith("_SWORD")|| 
    type.toString().endsWith("_PICKAXE") ||
    type.toString().endsWith("_AXE")||
    type.toString().endsWith("_SHOVEL")||
    type.toString().endsWith("_HOE");
  }

}