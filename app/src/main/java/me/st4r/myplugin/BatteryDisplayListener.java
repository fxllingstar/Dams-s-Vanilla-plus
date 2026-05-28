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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BatteryDisplayListener implements Listener, CommandExecutor {

    private final KineticGridListener gridManager;
    private final Set<UUID> enabledPlayers = new HashSet<>();

    public BatteryDisplayListener(KineticGridListener gridManager) {
        this.gridManager = gridManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!enabledPlayers.contains(player.getUniqueId())) return;
        if (event.getFrom().getChunk().equals(event.getTo() == null ? event.getFrom().getChunk() : event.getTo().getChunk())) return;

        Chunk chunk = player.getLocation().getChunk();
        sendBatteryMessage(player, chunk);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("batterydisplay")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        UUID playerId = player.getUniqueId();
        if (enabledPlayers.contains(playerId)) {
            enabledPlayers.remove(playerId);
            player.sendMessage("§8[§6Battery Display§8] §7Disabled.");
            return true;
        }

        enabledPlayers.add(playerId);
        player.sendMessage("§8[§6Battery Display§8] §aEnabled.");
        sendBatteryMessage(player, player.getLocation().getChunk());
        return true;
    }

    private void sendBatteryMessage(Player player, Chunk chunk) {
        double battery = gridManager.getGridBattery(chunk);
        int mode = gridManager.getGridMode(chunk);
        String modeName = getModeDisplayName(mode);
        player.sendMessage(String.format(
                "§8[§6Battery Display§8] §7Chunk §b(%d, %d) §7Battery: §e%.1f%% §8| §7Mode: §b%s",
                chunk.getX(), chunk.getZ(), battery, modeName
        ));
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
