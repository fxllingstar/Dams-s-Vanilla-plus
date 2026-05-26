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

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.PistonHead;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class KineticTrampolineListener implements Listener {

    private static final BlockFace[] PISTON_FACES = {
            BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
    private static final BlockFace[] CONNECTED_SLIME_FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
 
    private static final double MIN_HORIZONTAL_SPEED_SQUARED = 0.01D;
    private static final double SIDEWAYS_SPEED_MULTIPLIER = 1.8D;
    private static final double SIDEWAYS_VERTICAL_BASE = 0.35D;
    private static final double SIDEWAYS_VERTICAL_SPEED_MULTIPLIER = 0.9D;
    private static final double UPWARD_FORWARD_MULTIPLIER = 3.5D;
    private static final double UPWARD_VERTICAL_BASE = 0.85D;
    private static final double UPWARD_VERTICAL_SPEED_MULTIPLIER = 3.0D;
    private static final double MAX_VERTICAL_BOOST = 2.4D;
    private static final long FALL_PROTECTION_MILLIS = 15_000L; 
    private static final long LAUNCH_COOLDOWN_MILLIS = 400L;
    private static final int MAX_CONNECTED_SLIMES = 9; 

    private final NamespacedKey trampolineBoostKey;
    private final Map<UUID, Long> launchCooldowns = new HashMap<>();
    private final Map<UUID, Block> lastCheckedBlock = new HashMap<>();
    private final KineticGridListener gridManager;

   
   public KineticTrampolineListener(DVPlus plugin, KineticGridListener gridManager) {
    this.trampolineBoostKey = new NamespacedKey(plugin, "trampoline_boost_until");
    this.gridManager = gridManager; // Use the shared instance
}

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;

        Player player = event.getPlayer();
        Block currentBlock = event.getTo().getBlock();
        UUID playerId = player.getUniqueId();
        
        Block previousBlock = lastCheckedBlock.put(playerId, currentBlock);
        if (previousBlock != null && previousBlock.equals(currentBlock)) return;

        Block supportBlock = currentBlock.getRelative(BlockFace.DOWN);
        if (supportBlock.getType() != Material.SLIME_BLOCK) return;

        TrampolineStructure structure = findTrampolineStructure(supportBlock);
        if (structure == null) return;

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        Vector horizontalVelocity = new Vector(dx, 0, dz);
        double horizontalSpeedSquared = horizontalVelocity.lengthSquared();

        if (horizontalSpeedSquared < MIN_HORIZONTAL_SPEED_SQUARED) return;

        long now = System.currentTimeMillis();
        if (launchCooldowns.getOrDefault(playerId, 0L) > now) return;

      
        if (structure.copperMultiplier() == 4) {
            gridManager.modifyBattery(supportBlock.getChunk(), -5.0);
        }

        launchCooldowns.put(playerId, now + LAUNCH_COOLDOWN_MILLIS);
        launchPlayer(player, structure, horizontalVelocity, Math.sqrt(horizontalSpeedSquared), now);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.9F, 1.05F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;

        PersistentDataContainer data = player.getPersistentDataContainer();
        long protectionUntil = data.getOrDefault(trampolineBoostKey, PersistentDataType.LONG, 0L);
        if (protectionUntil == 0L) return;

        if (System.currentTimeMillis() > protectionUntil) {
            data.remove(trampolineBoostKey);
            return;
        }

        event.setCancelled(true);
        player.setFallDistance(0.0F);
        data.remove(trampolineBoostKey); 
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        launchCooldowns.remove(playerId);
        lastCheckedBlock.remove(playerId);
    }

    private void launchPlayer(Player player, TrampolineStructure structure, Vector horizontalVelocity, double speed, long launchTime) {
        BlockFace direction = structure.pistonFacing();
        int powerMultiplier = structure.copperMultiplier();
        Vector boostedVelocity = new Vector();

        if (direction == BlockFace.UP) {
            double forwardMultiplier = UPWARD_FORWARD_MULTIPLIER * powerMultiplier;
            boostedVelocity.setX(horizontalVelocity.getX() * forwardMultiplier);
            boostedVelocity.setZ(horizontalVelocity.getZ() * forwardMultiplier);

            double baseVerticalBoost = UPWARD_VERTICAL_BASE + (speed * UPWARD_VERTICAL_SPEED_MULTIPLIER);
            double verticalBoost = Math.min(MAX_VERTICAL_BOOST * powerMultiplier, baseVerticalBoost * powerMultiplier);
            boostedVelocity.setY(verticalBoost);
        } else {
            Vector directionalBoost = direction.getDirection().clone().setY(0).normalize()
                    .multiply(speed * SIDEWAYS_SPEED_MULTIPLIER * powerMultiplier);
            boostedVelocity.setX(directionalBoost.getX());
            boostedVelocity.setZ(directionalBoost.getZ());

            double baseVerticalBoost = SIDEWAYS_VERTICAL_BASE + (speed * SIDEWAYS_VERTICAL_SPEED_MULTIPLIER);
            double verticalBoost = Math.min(MAX_VERTICAL_BOOST * powerMultiplier, baseVerticalBoost * powerMultiplier);
            boostedVelocity.setY(verticalBoost);
        }

        player.setFallDistance(0.0F);
        player.setVelocity(boostedVelocity);
        player.getPersistentDataContainer().set(trampolineBoostKey, PersistentDataType.LONG, launchTime + FALL_PROTECTION_MILLIS);
    }

    private TrampolineStructure findTrampolineStructure(Block targetSlime) {
        Set<Block> slimeCluster = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();

        queue.add(targetSlime);
        slimeCluster.add(targetSlime);

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            for (BlockFace face : CONNECTED_SLIME_FACES) {
                if (slimeCluster.size() >= MAX_CONNECTED_SLIMES) break;

                Block neighbor = current.getRelative(face);
                if (neighbor.getType() == Material.SLIME_BLOCK && !slimeCluster.contains(neighbor)) {
                    slimeCluster.add(neighbor);
                    queue.add(neighbor);
                }
            }
            if (slimeCluster.size() >= MAX_CONNECTED_SLIMES) break;
        }

        for (Block slimeBlock : slimeCluster) {
            for (BlockFace pistonFacing : PISTON_FACES) {
                BlockFace toPiston = pistonFacing.getOppositeFace();
                Block targetBlock = slimeBlock.getRelative(toPiston);

                // Extended configuration pathway match check
                if (isMatchingPistonHead(targetBlock, pistonFacing)) {
                    Block pistonBase = targetBlock.getRelative(toPiston);
                    if (pistonBase.getType() == Material.STICKY_PISTON) {
                        if (pistonBase.getBlockData() instanceof Piston piston && piston.isExtended()) {
                            int copperMultiplier = countCopperBlocks(pistonBase, toPiston);
                            if (copperMultiplier > 0) return new TrampolineStructure(pistonFacing, copperMultiplier, slimeCluster.size());
                        }
                    }
                } 
                // Unextended block structure fallback check to match unextended pistons flush with slime
                else if (targetBlock.getType() == Material.STICKY_PISTON) {
                    if (targetBlock.getBlockData() instanceof Piston piston && !piston.isExtended() && piston.getFacing() == pistonFacing) {
                        int copperMultiplier = countCopperBlocks(targetBlock, toPiston);
                        if (copperMultiplier > 0) return new TrampolineStructure(pistonFacing, copperMultiplier, slimeCluster.size());
                    }
                }
            }
        }
        return null;
    }

    private int countCopperBlocks(Block pistonBase, BlockFace copperDirection) {
        int copperBlocks = 0;
        org.bukkit.Chunk chunk = pistonBase.getChunk();
        
        boolean isGalvanized = gridManager.getGridBattery(chunk) > 5.0;
        int maxCap = isGalvanized ? 4 : 3;

        for (int distance = 1; distance <= maxCap; distance++) {
            Block candidate = pistonBase.getRelative(copperDirection, distance);
            // Matches all copper types and variants registered inside the grid network
            if (!gridManager.isConductiveMaterial(candidate.getType())) break;
            copperBlocks++;
        }
        return copperBlocks;
    }

    private boolean isMatchingPistonHead(Block block, BlockFace pistonFacing) {
        return block.getType() == Material.PISTON_HEAD && block.getBlockData() instanceof PistonHead head && head.getFacing() == pistonFacing;
    }

    private record TrampolineStructure(BlockFace pistonFacing, int copperMultiplier, int connectedSlimes) {}
}