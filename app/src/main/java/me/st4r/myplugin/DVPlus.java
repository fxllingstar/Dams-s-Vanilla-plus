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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.meta.Damageable;

import java.util.Arrays;

public final class DVPlus extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        MessengerParrotListener parrotListener = new MessengerParrotListener(this);
        getServer().getPluginManager().registerEvents(parrotListener, this);
        getCommand("parrot").setExecutor(parrotListener);
        getServer().getPluginManager().registerEvent(new LuminousItemListener(this), this);
        getServer().getPluginManager().registerEvents(new SmithingTableListener(), this);
        getServer().getPluginManager().registerEvents(new HitchMechanicListener(this), this);
        getServer().getPluginManager().registerEvents(new LunarHarvestingListener(), this);
        getLogger().info("----------------------------------");
        getLogger().info("Dams's Vanilla+ Enabled.");
        getLogger().info("'To become a star, you must burn.'");
        getLogger().info("----------------------------------");
        
         new LightEmissionTask(this).runTaskTimer(this, 0L, 1L);
        startCauldronFrostTasks();
    }

    // -----------------------------------------------------------------
    // Rotten Flesh purification on campfire (2 minutes)
    // -----------------------------------------------------------------
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.ROTTEN_FLESH) return;
        Block below = event.getBlock().getRelative(BlockFace.DOWN);
        if (below.getType() != Material.CAMPFIRE && below.getType() != Material.SOUL_CAMPFIRE) return;

        Block flesh = event.getBlock();
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            if (flesh.getType() == Material.ROTTEN_FLESH) {
                flesh.setType(Material.LEATHER);
            }
        }, 2400L);
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

            event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HASTE,    12000, 0, true, false));
            event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 12000, 0, true, false));
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
