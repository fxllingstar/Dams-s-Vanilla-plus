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

        ItemStack template = inv.getItem(0);   // Template slot
        ItemStack base = inv.getItem(1);       // Base item (tool/armor)
        ItemStack addition = inv.getItem(2);   // Addition (glow ink sac)


    if (base == null || addition == null) return;
    if (addition.getType() != Material.GLOW_INK_SAC) return;
    if (template == null || template.getType() != Material.GLOW_ITEM_FRAME) return;
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
    String name = type.toString();
    return
    name.endsWith("_HELMET") ||
    name.endsWith("_CHESTPLATE") ||
    name.endsWith("_LEGGINGS") ||
    name.endsWith("_BOOTS") ||
    name.endsWith("_SWORD") ||
    name.endsWith("_PICKAXE") ||
    name.endsWith("_AXE") ||
    name.endsWith("_SHOVEL") ||
    name.endsWith("_HOE") ||
    name.equals("NETHERITE_SWORD");
  }

}