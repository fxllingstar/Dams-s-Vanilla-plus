/*
 * DVPlus (Dams's Vanilla +)
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
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.meta.Damageable;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;


import org.bukkit.NamespacedKey;

public final class DVPlus extends JavaPlugin implements Listener {

    public static final NamespacedKey LUMINOUS_KEY = new NamespacedKey("dvplus", "luminous_time");
    private EchoSonarListener echoSonarListener;


    
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        MessengerParrotListener parrotListener = new MessengerParrotListener(this);
        echoSonarListener = new EchoSonarListener(this);
        KineticGridListener gridListener = new KineticGridListener();
        GcgAutomationListener gcgListener = new GcgAutomationListener(gridListener);
        getServer().getPluginManager().registerEvents(parrotListener, this);
        getServer().getPluginManager().registerEvents(echoSonarListener, this);
        getCommand("parrot").setExecutor(parrotListener);
        getServer().getPluginManager().registerEvents(new SmithingTableListener(), this);
        getServer().getPluginManager().registerEvents(new HitchMechanicListener(this), this);
        getServer().getPluginManager().registerEvents(new KineticTrampolineListener(this, gridListener), this);
        getServer().getPluginManager().registerEvents(new LunarHarvestingListener(), this);
        getServer().getPluginManager().registerEvents(gridListener, this);
        getServer().getPluginManager().registerEvents(gcgListener, this);
        getCommand("gcg").setExecutor(gcgListener);
        getLogger().info("----------------------------------");
        getLogger().info("Dams's Vanilla + Enabled.");
        getLogger().info("'To become a star, you must burn.'");
        getLogger().info("----------------------------------");
        
         new LightEmissionTask(this).runTaskTimer(this, 0L, 1L);
         new LuminousDecayTask().runTaskTimer(this, 20L, 20L); 
        startCauldronFrostTasks();  
        registerLuminousRecipes();
    }

    @Override
    public void onDisable() {
        if (echoSonarListener != null) {
            echoSonarListener.shutdown();
        }
    }


    //======================================================
   //    HASHMAPS
   //=======================================================
 private final Map<Location, Integer> campfireCrops = new HashMap<>();
 private final java.util.Set<Location> trackedCauldrons = new java.util.HashSet<>();



    // -----------------------------------------------------------------
    // Rotten Flesh purification on campfire (2 minutes)
    // -----------------------------------------------------------------

@EventHandler
public void onCampfireInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getClickedBlock() == null) return;
    
    Block campfire = event.getClickedBlock();
    if (campfire.getType() != Material.CAMPFIRE &&
        campfire.getType() != Material.SOUL_CAMPFIRE) return;

    ItemStack item = event.getItem();
    if (item == null || item.getType() != Material.ROTTEN_FLESH) return;
    Location loc = campfire.getLocation();
    int currentCount = campfireCrops.getOrDefault(loc, 0);

    if (currentCount >= 5) {
        event.getPlayer().sendMessage("§cCampfire is full, use another one");
        return;
    }


    event.setCancelled(true);
    item.setAmount(item.getAmount() - 1);
    campfireCrops.put(loc, currentCount + 1);

    event.getPlayer().sendMessage("§eThe rotten flesh is beginning to cook; it will be turned into leather in 2 minutes.");
    Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
        if (campfire.getWorld() != null) {
            campfire.getWorld().dropItemNaturally(loc.clone().add(0.5, 1, 0.5), new ItemStack(Material.LEATHER));
        }

        int newCount = campfireCrops.getOrDefault(loc, 0) - 1;
        if (newCount <= 0) {
            campfireCrops.remove(loc);
        } else {
            campfireCrops.put(loc, newCount);
        }
    }, 2400L); 
}


  //-------------------------------------------------------------------
  //Luminous Item Registration
  //-------------------------------------------------------------------
  private void registerLuminousRecipes() {
 
    for (Material mat : Material.values()) {
        String name = mat.toString();
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || 
            name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || 
            name.endsWith("_SWORD") || name.endsWith("_PICKAXE") || 
            name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {

            NamespacedKey key = new NamespacedKey(this, "luminous_" + name.toLowerCase());
            
    
            SmithingTransformRecipe recipe = new SmithingTransformRecipe(
                key,
                new ItemStack(mat), 
                new RecipeChoice.MaterialChoice(Material.GLOW_ITEM_FRAME), 
                new RecipeChoice.MaterialChoice(mat), // The Base tool
                new RecipeChoice.MaterialChoice(Material.GLOW_INK_SAC) // The Addition
            );

            Bukkit.addRecipe(recipe);
        }
    }
}

@EventHandler
public void onCauldronPlace(org.bukkit.event.block.BlockPlaceEvent event) {
    if (event.getBlock().getType() == Material.CAULDRON) {
        trackedCauldrons.add(event.getBlock().getLocation());
    }
}

@EventHandler
public void onCauldronBreak(org.bukkit.event.block.BlockBreakEvent event) {
    if (event.getBlock().getType() == Material.CAULDRON) {
        trackedCauldrons.remove(event.getBlock().getLocation());
    }
}
    // -----------------------------------------------------------------
    // Stonecutter tool sharpening
    // -----------------------------------------------------------------
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.STONECUTTER) return;
        handleStonecutterSharpening(event);
    }                 

    private void handleStonecutterSharpening(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !isToolItem(item)) return;
        if (!(item.getItemMeta() instanceof Damageable damageable)) return;

        int damage = damageable.getDamage();
        int maxDurability = item.getType().getMaxDurability();

        if (damage + 50 <= maxDurability) {
            damageable.setDamage(damage + 50);
            item.setItemMeta(damageable);

            String toolType = item.getType().toString();
            if (toolType.endsWith("_PICKAXE")) {
                event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 12000, 0, true, false));
            } else if (toolType.endsWith("_SWORD")) {
                event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 12000, 0, true, false));
            }

            event.getPlayer().sendMessage("§aYour tool has been sharpened!");
            event.setCancelled(true);
        } else {
            event.getPlayer().sendMessage("§cYour tool doesn't have enough durability!");
        }
    }

    // -----------------------------------------------------------------
    // Frost-Bound Cauldron → Blue Ice in cold biomes overnight
    // -----------------------------------------------------------------
    //NOTE: TO BE CHANGED
    private void startCauldronFrostTasks() {
      Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
     java.util.Iterator<Location> iterator = trackedCauldrons.iterator();
      
     while(iterator.hasNext()){
        Location loc = iterator.next();
        if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX()>> 4, loc.getBlockZ() >> 4)){
            continue;
        }
        Block block = loc.getBlock();
        Material type = block.getType();
        if(type != Material.CAULDRON && type != Material.WATER_CAULDRON){
            iterator.remove();
            continue;
        }
     //Temperature check? Might need a rework to get individual biomes
        if (block.getTemperature() < 0.15 && type == Material.WATER_CAULDRON){
            if (Math.random() < 0.2){
                block.setType(Material.CAULDRON);
                block.getWorld().dropItemNaturally(
                    loc.clone().add(0.5, 1, 0.5),
                    new ItemStack(Material.BLUE_ICE)
                );
            }

        }
  }
 },0L, 24000L);
}

    private boolean isToolItem(ItemStack item) {
        return switch (item.getType()) {
            case WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE,
                 WOODEN_AXE,     STONE_AXE,     IRON_AXE,     DIAMOND_AXE,     NETHERITE_AXE,
                 WOODEN_SWORD,   STONE_SWORD,   IRON_SWORD,   DIAMOND_SWORD,   NETHERITE_SWORD -> true;
            default -> false;
        };
    }
}
//The morning light is blue the feeling is bizzaree~
//The night is almost over, I still don't know where you are
