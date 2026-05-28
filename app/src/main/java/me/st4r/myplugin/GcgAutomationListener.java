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

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GcgAutomationListener implements Listener, CommandExecutor, TabCompleter {

    private static final long DIAGNOSTIC_COOLDOWN_MILLIS = 500L;

    private final KineticGridListener gridManager;
    private final Map<Long, Long> sculkCooldowns = new HashMap<>();
    private final Map<Long, Boolean> sculkCache = new HashMap<>();
    private final Map<UUID, Long> batteryViewerCooldowns = new HashMap<>();
    private final Map<UUID, Boolean> batteryViewerEnabled = new HashMap<>();

    public GcgAutomationListener(KineticGridListener gridManager) {
        this.gridManager = gridManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster monster)) return;
        handleHostileMob(monster, monster.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobMove(EntityMoveEvent event) {
        if (!(event.getEntity() instanceof Monster monster)) return;
        if (!event.hasChangedBlock()) return;

        Location to = event.getTo();
        if (to == null) return;
        if (event.getFrom() != null && event.getFrom().getChunk().equals(to.getChunk())) return;

        handleHostileMob(monster, to);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom() == null || event.getTo() == null) return;
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;

        Player player = event.getPlayer();
        if (!batteryViewerEnabled.getOrDefault(player.getUniqueId(), false)) return;

        sendBatteryStatus(player, event.getTo().getChunk());
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

    private void handleHostileMob(Monster monster, Location location) {
        Chunk chunk = location.getChunk();
        int mode = gridManager.getGridMode(chunk);
        if (mode == 1 || mode == 4) return;

        double battery = gridManager.getGridBattery(chunk);
        if (battery <= 0.5) return;

        long chunkKey = ((long) chunk.getX() << 32) | (chunk.getZ() & 0xFFFFFFFFL);
        if (hasSculkSensorCached(chunk, chunkKey)) return;

        location.getWorld().spawn(location, EvokerFangs.class);
        monster.damage(100.0);
        gridManager.modifyBattery(chunk, -0.5);
    }

    private void sendBatteryStatus(Player player, Chunk chunk) {
        long now = System.currentTimeMillis();
        if (batteryViewerCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) return;

        double charge = gridManager.getGridBattery(chunk);
        int mode = gridManager.getGridMode(chunk);
        String modeName = getModeDisplayName(mode);
        player.sendMessage(String.format("§8[§6Grid Monitor§8] §7Field Energy: §e%.1f%% §8| §7Mode: §b%s", charge, modeName));
        batteryViewerCooldowns.put(player.getUniqueId(), now + DIAGNOSTIC_COOLDOWN_MILLIS);
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
        if (cmd.getName().equalsIgnoreCase("displaybattery")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cThis command can only be used by players.");
                return true;
            }

            UUID playerId = player.getUniqueId();
            boolean enabled = !batteryViewerEnabled.getOrDefault(playerId, false);
            batteryViewerEnabled.put(playerId, enabled);

            if (enabled) {
                player.sendMessage("§8[§6Grid Monitor§8] §7Battery viewer §aenabled§7.");
            } else {
                batteryViewerCooldowns.remove(playerId);
                player.sendMessage("§8[§6Grid Monitor§8] §7Battery viewer §cdisabled§7.");
            }
            return true;
        }

        if (!cmd.getName().equalsIgnoreCase("gcg")) return false;

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            sendGcgUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("mode")) {
            if (args.length < 2) {
                sendGcgUsage(sender);
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

        if (args[0].equalsIgnoreCase("transfer")) {
            if (args.length < 3) {
                sendGcgUsage(sender);
                return true;
            }

            int fromChunkX;
            int fromChunkZ;
            try {
                fromChunkX = Integer.parseInt(args[1]);
                fromChunkZ = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cChunk coordinates must be whole numbers.");
                return true;
            }

            Chunk targetChunk = player.getLocation().getChunk();
            Chunk sourceChunk = player.getWorld().getChunkAt(fromChunkX, fromChunkZ);
            double moved = gridManager.transferBattery(sourceChunk, targetChunk);
            if (moved <= 0.0) {
                sender.sendMessage("§cNo battery was transferred. Make sure both chunks are connected by powered redstone/lightning rods.");
                return true;
            }

            player.sendMessage(String.format(
                    "§8[§6GCG§8] §7Transferred §e%.1f%% §7from chunk §b%d,%d §7to §b%d,%d§7.",
                    moved, fromChunkX, fromChunkZ, targetChunk.getX(), targetChunk.getZ()));
            return true;
        }

        sendGcgUsage(sender);
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("displaybattery")) {
            return List.of();
        }

        if (!cmd.getName().equalsIgnoreCase("gcg")) {
            return List.of();
        }

        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            if ("mode".startsWith(prefix)) {
                suggestions.add("mode");
            }
            if ("transfer".startsWith(prefix)) {
                suggestions.add("transfer");
            }
            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            for (int i = 0; i <= 4; i++) {
                String value = Integer.toString(i);
                if (value.startsWith(args[1])) {
                    suggestions.add(value);
                }
            }
        }

        return suggestions;
    }

    private void sendGcgUsage(CommandSender sender) {
        sender.sendMessage("§8[§6GCG§8] §7Usage:");
        sender.sendMessage("§7/gcg mode <0-4>");
        sender.sendMessage("§7/gcg transfer <fromChunkX> <fromChunkZ>");
        sender.sendMessage("§8Run transfer in the chunk that should receive the battery.");
    }
}
