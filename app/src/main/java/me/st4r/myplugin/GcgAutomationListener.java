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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GcgAutomationListener implements Listener, CommandExecutor, TabCompleter {

    private final KineticGridListener gridManager;
    private final Map<Long, Long> sculkCooldowns = new HashMap<>();
    private final Map<Long, Boolean> sculkCache = new HashMap<>();

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
            sender.sendMessage("\u00A7cThis command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("\u00A7cUsage: /gcg mode <0-4>");
            sender.sendMessage("\u00A7cUsage: /gcg transfer <fromChunkX> <fromChunkZ> <toChunkX> <toChunkZ> [amount]");
            return true;
        }

        if (args[0].equalsIgnoreCase("mode")) {
            return handleModeCommand(player, sender, args);
        }

        if (args[0].equalsIgnoreCase("transfer")) {
            return handleTransferCommand(player, sender, args);
        }

        sender.sendMessage("\u00A7cUnknown subcommand. Use /gcg mode or /gcg transfer.");
        return true;
    }

    private boolean handleModeCommand(Player player, CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("\u00A7cUsage: /gcg mode <0-4>");
            return true;
        }

        int mode;
        try {
            mode = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("\u00A7cMode must be a number between 0 and 4.");
            return true;
        }

        if (mode < 0 || mode > 4) {
            sender.sendMessage("\u00A7cMode must be between 0 and 4.");
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        gridManager.setGridMode(chunk, mode);
        String modeName = getModeDisplayName(mode);
        player.sendMessage(String.format("\u00A78[\u00A76GCG\u00A78] \u00A77Mode set to \u00A7b%s \u00A77(\u00A7e%d\u00A77)", modeName, mode));
        return true;
    }

    private boolean handleTransferCommand(Player player, CommandSender sender, String[] args) {
        if (args.length != 5 && args.length != 6) {
            sender.sendMessage("\u00A7cUsage: /gcg transfer <fromChunkX> <fromChunkZ> <toChunkX> <toChunkZ> [amount]");
            return true;
        }

        int fromX;
        int fromZ;
        int toX;
        int toZ;
        try {
            fromX = Integer.parseInt(args[1]);
            fromZ = Integer.parseInt(args[2]);
            toX = Integer.parseInt(args[3]);
            toZ = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("\u00A7cChunk coordinates must be whole numbers.");
            return true;
        }

        Chunk fromChunk = player.getWorld().getChunkAt(fromX, fromZ);
        Chunk toChunk = player.getWorld().getChunkAt(toX, toZ);

        if (gridManager.sharesBatterySource(fromChunk, toChunk)) {
            sender.sendMessage("\u00A7cThose chunks already share the same power source.");
            return true;
        }

        double sourceBattery = gridManager.getGridBattery(fromChunk);
        if (sourceBattery <= 0.0) {
            sender.sendMessage("\u00A7cThe source chunk has no electricity to transfer.");
            return true;
        }

        double amount = sourceBattery;
        if (args.length == 6) {
            try {
                amount = Double.parseDouble(args[5]);
            } catch (NumberFormatException e) {
                sender.sendMessage("\u00A7cAmount must be a positive number.");
                return true;
            }
            if (amount <= 0.0) {
                sender.sendMessage("\u00A7cAmount must be greater than 0.");
                return true;
            }
        }

        double moved = gridManager.transferBattery(fromChunk, toChunk, amount);
        if (moved <= 0.0) {
            sender.sendMessage("\u00A7cNo electricity was moved. The destination chunk may already be full.");
            return true;
        }

        double fromRemaining = gridManager.getGridBattery(fromChunk);
        double toNow = gridManager.getGridBattery(toChunk);
        sender.sendMessage(String.format(
                "\u00A78[\u00A76GCG\u00A78] \u00A7aTransferred \u00A7e%.1f%%\u00A7a power from \u00A7b(%d, %d)\u00A7a to \u00A7b(%d, %d)\u00A7a. \u00A77Source: \u00A7e%.1f%%\u00A77, Dest: \u00A7e%.1f%%",
                moved, fromX, fromZ, toX, toZ, fromRemaining, toNow
        ));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("gcg")) {
            return List.of();
        }

        if (args.length == 1) {
            return filterSuggestions(args[0], List.of("mode", "transfer"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return filterSuggestions(args[1], List.of("0", "1", "2", "3", "4"));
        }

        if (args[0].equalsIgnoreCase("transfer")) {
            if (sender instanceof Player player) {
                int currentX = player.getLocation().getChunk().getX();
                int currentZ = player.getLocation().getChunk().getZ();
                List<String> suggestions = new ArrayList<>();

                if (args.length == 2) {
                    suggestions.add(String.valueOf(currentX));
                    return filterSuggestions(args[1], suggestions);
                }
                if (args.length == 3) {
                    suggestions.add(String.valueOf(currentZ));
                    return filterSuggestions(args[2], suggestions);
                }
                if (args.length == 4) {
                    suggestions.add(String.valueOf(currentX + 1));
                    suggestions.add(String.valueOf(currentX - 1));
                    suggestions.add(String.valueOf(currentX));
                    return filterSuggestions(args[3], suggestions);
                }
                if (args.length == 5) {
                    suggestions.add(String.valueOf(currentZ + 1));
                    suggestions.add(String.valueOf(currentZ - 1));
                    suggestions.add(String.valueOf(currentZ));
                    return filterSuggestions(args[4], suggestions);
                }
            }
        }

        return List.of();
    }

    private List<String> filterSuggestions(String token, List<String> suggestions) {
        if (token == null || token.isEmpty()) {
            return suggestions;
        }

        String lowerToken = token.toLowerCase();
        return suggestions.stream()
                .filter(suggestion -> suggestion.toLowerCase().startsWith(lowerToken))
                .toList();
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

    

    private void sendGcgUsage(CommandSender sender) {
        sender.sendMessage("§8[§6GCG§8] §7Usage:");
        sender.sendMessage("§7/gcg mode <0-4>");
        sender.sendMessage("§7/gcg transfer <fromChunkX> <fromChunkZ>");
        sender.sendMessage("§8Run transfer in the chunk that should receive the battery.");
    }
}

