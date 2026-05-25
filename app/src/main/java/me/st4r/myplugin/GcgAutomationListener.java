package me.st4r.myplugin;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import java.util.HashMap;
import java.util.Map;

public class GcgAutomationListener implements Listener, CommandExecutor {

    private final KineticGridListener gridManager;
    private final Map<Long, Long> sculkCooldowns = new HashMap<>();
    private final Map<Long, Boolean> sculkCache = new HashMap<>();

    public GcgAutomationListener(KineticGridListener gridManager) {
        this.gridManager = gridManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster monster)) return;

        Chunk chunk = monster.getLocation().getChunk();
        int mode = gridManager.getGridMode(chunk);

        if (mode == 1 || mode == 4) return;

        double battery = gridManager.getGridBattery(chunk);
        if (battery <= 0.5) return;

        long chunkKey = ((long) chunk.getX() << 32) | (chunk.getZ() & 0xFFFFFFFFL);
        if (hasSculkSensorCached(chunk, chunkKey)) return;

        Location loc = monster.getLocation();
        loc.getWorld().spawn(loc, EvokerFangs.class);

        monster.damage(100.0);
        gridManager.modifyBattery(chunk, -0.5);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        Block crop = event.getBlock();
        Chunk chunk = crop.getChunk();
        int mode = gridManager.getGridMode(chunk);

        if (mode != 0 && mode != 1) return;

        if (gridManager.getGridBattery(chunk) < 0.1) return;

        if (!hasAdjacentCopperGrate(crop)) return;

        org.bukkit.block.BlockState newState = event.getNewState();
        org.bukkit.block.data.BlockData blockData = newState.getBlockData();

        if (blockData instanceof Ageable ageable) {
            int maxAge = ageable.getMaximumAge();
            int currentAge = ageable.getAge();

            if (currentAge < maxAge) {
                ageable.setAge(currentAge + 1);
                newState.setBlockData(blockData);
                gridManager.modifyBattery(chunk, -0.1);
            }
        }
    }

    private boolean hasAdjacentCopperGrate(Block crop) {
        for (BlockFace face : BlockFace.values()) {
            if (face == BlockFace.SELF) continue;
            if (crop.getRelative(face).getType() == Material.COPPER_GRATE) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onDiagnosticCheck(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.GOLD_BLOCK) return;

        if (block.getBlockPower() > 0) {
            if (!isConnectedToGrid(block)) return;

            double charge = gridManager.getGridBattery(block.getChunk());
            int mode = gridManager.getGridMode(block.getChunk());
            String modeName = getModeDisplayName(mode);
            event.getPlayer().sendMessage(String.format("§8[§6Grid Monitor§8] §7Field Energy: §e%.1f%% §8| §7Mode: §b%s", charge, modeName));
            event.setCancelled(true);
        }
    }

    private boolean isConnectedToGrid(Block block) {
        for (BlockFace face : BlockFace.values()) {
            if (face == BlockFace.SELF) continue;
            if (gridManager.isConductiveMaterial(block.getRelative(face).getType())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSculkSensorCached(Chunk chunk, long key) {
        long now = System.currentTimeMillis();
        if (sculkCooldowns.getOrDefault(key, 0L) > now) {
            return sculkCache.getOrDefault(key, false);
        }

        boolean found = false;
        for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
                for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y += 8) {
                    if (chunk.getBlock(x, y, z).getType() == Material.SCULK_SENSOR) {
                        found = true;
                        break;
                    }
                }
            }
        }
        sculkCache.put(key, found);
        sculkCooldowns.put(key, now + 5000L);
        return found;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("gcg")) return false;

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("mode")) {
            sender.sendMessage("§cUsage: /gcg mode <0-4>");
            return true;
        }

        int mode;
        try {
            mode = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cMode must be a number between 0 and 4.");
            return true;
        }

        if (mode < 0 || mode > 4) {
            sender.sendMessage("§cMode must be between 0 and 4.");
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        gridManager.setGridMode(chunk, mode);
        String modeName = getModeDisplayName(mode);
        player.sendMessage(String.format("§8[§6GCG§8] §7Mode set to §b%s §7(§e%d§7)", modeName, mode));
        return true;
    }

    private String getModeDisplayName(int mode) {
        return switch (mode) {
            case 0 -> "ALL";
            case 1 -> "GREENHOUSE";
            case 2 -> "HARVESTER";
            case 3 -> "MOB BLOCKER";
            case 4 -> "DISABLED";
            default -> "UNKNOWN";
        };
    }
}