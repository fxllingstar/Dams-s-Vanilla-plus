package me.st4r.myplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.meta.Damageable;

import java.util.Arrays;
import org.bukkit.NamespacedKey;

public final class DVPlus extends JavaPlugin implements Listener {

    public static final NamespacedKey LUMINOUS_KEY = new NamespacedKey("dvplus", "luminous_time");

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        MessengerParrotListener parrotListener = new MessengerParrotListener(this);
        getServer().getPluginManager().registerEvents(parrotListener, this);
        getCommand("parrot").setExecutor(parrotListener);
        getServer().getPluginManager().registerEvents(new SmithingTableListener(), this);
        getServer().getPluginManager().registerEvents(new HitchMechanicListener(this), this);
        getServer().getPluginManager().registerEvents(new LunarHarvestingListener(), this);
      
        getLogger().info("----------------------------------");
        getLogger().info("Dams's Vanilla+ Enabled.");
        getLogger().info("'To become a star, you must burn.'");
        getLogger().info("----------------------------------");
        
         new LightEmissionTask(this).runTaskTimer(this, 0L, 1L);
         new LuminousDecayTask().runTaskTimer(this, 20L, 20L); 
        startCauldronFrostTasks();  
        registerLuminousRecipes();
    }

    // -----------------------------------------------------------------
    // Rotten Flesh purification on campfire (2 minutes)
    // -----------------------------------------------------------------
    @EventHandler
    public void onCampfireInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.CAMPFIRE &&
            event.getClickedBlock().getType() != Material.SOUL_CAMPFIRE) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ROTTEN_FLESH) return;

        event.setCancelled(true);
        Block campfire = event.getClickedBlock();
        ItemStack flesh = item.clone();
        flesh.setAmount(1);

        item.setAmount(item.getAmount() - 1);

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            campfire.getWorld().dropItemNaturally(campfire.getLocation().add(0.5, 1, 0.5), new ItemStack(Material.LEATHER));
        }, 2400L); // 2 minutes
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

        if (damage + 10 <= maxDurability) {
            damageable.setDamage(damage + 10);
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
    private void startCauldronFrostTasks() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () ->
            Bukkit.getServer().getWorlds().forEach(world -> {
                if (world.getEnvironment() != org.bukkit.World.Environment.NORMAL) return;
                Arrays.stream(world.getLoadedChunks()).forEach(chunk -> {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                                Block block = chunk.getBlock(x, y, z);
                                if (block.getType() == Material.CAULDRON && Math.random() < 0.1) {
                                    block.setType(Material.BLUE_ICE);
                                }
                            }
                        }
                    }
                });
            }), 0L, 24000L);
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
