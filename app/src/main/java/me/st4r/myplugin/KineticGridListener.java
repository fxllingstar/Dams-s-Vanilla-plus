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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class KineticGridListener implements Listener  {

    private static final int MAX_WIRE_LENGTH = 16;
    private static final double MAX_BATTERY = 100.0;
    private static final double NATURAL_LIGHTNING_CHARGE = 100.0;
    private static final double TRIDENT_CHARGE_AMOUNT = 20.0;
    private static final double TRIDENT_CHANCE = 0.20;

    private static final BlockFace[] CONDUCTIVE_FACES = {
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    };

    private static final Set<String> CONDUCTIVE_MATERIALS = new HashSet<>(Arrays.asList(
            "COPPER_BLOCK",
            "EXPOSED_COPPER",
            "WEATHERED_COPPER",
            "OXIDIZED_COPPER",
            "WAXED_COPPER_BLOCK",
            "WAXED_EXPOSED_COPPER",
            "WAXED_WEATHERED_COPPER",
            "WAXED_OXIDIZED_COPPER",
            "COPPER_GRATE",
            "EXPOSED_COPPER_GRATE",
            "WEATHERED_COPPER_GRATE",
            "OXIDIZED_COPPER_GRATE",
            "WAXED_COPPER_GRATE",
            "WAXED_EXPOSED_COPPER_GRATE",
            "WAXED_WEATHERED_COPPER_GRATE",
            "WAXED_OXIDIZED_COPPER_GRATE"
    ));

    private final NamespacedKey sourceBatteryKey;
    private final NamespacedKey linkedSourceKey;
    private final NamespacedKey gcgModeKey;

    public KineticGridListener() {
        this.sourceBatteryKey = new NamespacedKey("dvplus", "gcg_source_battery");
        this.linkedSourceKey = new NamespacedKey("dvplus", "gcg_linked_source_chunk");
        this.gcgModeKey = new NamespacedKey("dvplus", "gcg_mode");
    }

    public KineticGridListener(DVPlus plugin) {
        this.sourceBatteryKey = new NamespacedKey(plugin, "gcg_source_battery");
        this.linkedSourceKey = new NamespacedKey(plugin, "gcg_linked_source_chunk");
        this.gcgModeKey = new NamespacedKey(plugin, "gcg_mode");
    }

    @EventHandler(ignoreCancelled = true)
    public void onLightningStrike(LightningStrikeEvent event) {
        Block strikeBlock = event.getLightning().getLocation().getBlock();
        if (!isLightningRod(strikeBlock.getType())) return;

        double chargeToApply = NATURAL_LIGHTNING_CHARGE;
        String chargeSource = "lightning";
        Player recipient = null;

        if (event.getCause() == LightningStrikeEvent.Cause.TRIDENT) {
            chargeToApply = resolveTridentCharge(strikeBlock);
            chargeSource = "trident";
            recipient = event.getLightning().getCausingPlayer();
        }

        if (chargeToApply <= 0.0) return;
        chargeGridNetwork(strikeBlock, chargeToApply);
        sendChargeFeedback(strikeBlock, chargeSource, recipient);
    }

    public double getGridBattery(Chunk currentChunk) {
        PersistentDataContainer pdc = currentChunk.getPersistentDataContainer();
        if (pdc.has(sourceBatteryKey, PersistentDataType.DOUBLE)) {
            return clampBattery(pdc.getOrDefault(sourceBatteryKey, PersistentDataType.DOUBLE, 0.0));
        }

        Chunk sourceChunk = resolveSourceChunk(currentChunk);
        if (sourceChunk == null) return 0.0;

        return clampBattery(sourceChunk.getPersistentDataContainer()
                .getOrDefault(sourceBatteryKey, PersistentDataType.DOUBLE, 0.0));
    }

    public void modifyBattery(Chunk currentChunk, double delta) {
        PersistentDataContainer targetPdc = resolveBatteryContainer(currentChunk);
        double current = targetPdc.getOrDefault(sourceBatteryKey, PersistentDataType.DOUBLE, 0.0);
        targetPdc.set(sourceBatteryKey, PersistentDataType.DOUBLE, clampBattery(current + delta));
    }

    private void chargeGridNetwork(Block startBlock, double chargeAmount) {
        Chunk sourceChunk = startBlock.getChunk();
        long sourceChunkKey = getChunkKey(sourceChunk);
        PersistentDataContainer sourcePdc = sourceChunk.getPersistentDataContainer();

        double currentCharge = sourcePdc.getOrDefault(sourceBatteryKey, PersistentDataType.DOUBLE, 0.0);
        sourcePdc.set(sourceBatteryKey, PersistentDataType.DOUBLE, clampBattery(currentCharge + chargeAmount));

        Queue<Block> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(startBlock);

        while (!queue.isEmpty()) {
            Block current = queue.poll();

            if (!current.getChunk().equals(sourceChunk)) {
                current.getChunk().getPersistentDataContainer()
                        .set(linkedSourceKey, PersistentDataType.LONG, sourceChunkKey);
            }

            for (BlockFace face : CONDUCTIVE_FACES) {
                if (visited.size() >= MAX_WIRE_LENGTH) break;

                Block neighbor = current.getRelative(face);
                if (!isConductiveMaterial(neighbor.getType())) continue;

                if (visited.add(neighbor.getBlockKey())) {
                    queue.add(neighbor);
                }
            }
        }
    }

    private void sendChargeFeedback(Block strikeBlock, String source, Player directRecipient) {
        Component message = Component.text("Chunk charged with " + source + "!", NamedTextColor.AQUA);

        if (directRecipient != null && directRecipient.isOnline()) {
            directRecipient.sendActionBar(message);
            return;
        }

        List<Player> nearbyPlayers = strikeBlock.getWorld().getPlayers().stream()
                .filter(player -> player.getWorld().equals(strikeBlock.getWorld()))
                .filter(player -> player.getLocation().distanceSquared(strikeBlock.getLocation()) <= 4096.0D)
                .toList();

        for (Player player : nearbyPlayers) {
            player.sendActionBar(message);
        }
    }

    private Chunk resolveSourceChunk(Chunk currentChunk) {
        Long sourceKey = currentChunk.getPersistentDataContainer().get(linkedSourceKey, PersistentDataType.LONG);
        if (sourceKey == null) return null;

        int sourceX = (int) (sourceKey >> 32);
        int sourceZ = (int) (sourceKey.longValue());
        return currentChunk.getWorld().getChunkAt(sourceX, sourceZ);
    }

    private PersistentDataContainer resolveBatteryContainer(Chunk currentChunk) {
        PersistentDataContainer local = currentChunk.getPersistentDataContainer();
        if (local.has(sourceBatteryKey, PersistentDataType.DOUBLE)) {
            return local;
        }

        Chunk sourceChunk = resolveSourceChunk(currentChunk);
        if (sourceChunk == null) return local;

        return sourceChunk.getPersistentDataContainer();
    }

    public boolean isConductiveMaterial(Material type) {
        return CONDUCTIVE_MATERIALS.contains(type.name());
    }

    private double resolveTridentCharge(Block lightningRod) {
        if (!lightningRod.getWorld().hasStorm()) {
            return 0.0;
        }

        return ThreadLocalRandom.current().nextDouble() < TRIDENT_CHANCE ? TRIDENT_CHARGE_AMOUNT : 0.0;
    }

    private double clampBattery(double battery) {
        return Math.max(0.0, Math.min(MAX_BATTERY, battery));
    }

    private long getChunkKey(Chunk chunk) {
        return ((long) chunk.getX() << 32) | (chunk.getZ() & 0xFFFFFFFFL);
    }

    public int getGridMode(Chunk chunk) {
        return chunk.getPersistentDataContainer().getOrDefault(gcgModeKey, PersistentDataType.INTEGER, 0);
    }

    public void setGridMode(Chunk chunk, int mode) {
        int clamped = Math.max(0, Math.min(4, mode));
        chunk.getPersistentDataContainer().set(gcgModeKey, PersistentDataType.INTEGER, clamped);
    }

    private boolean isLightningRod(Material material) {
        String name = material.name();
        return name.endsWith("LIGHTNING_ROD");
    }
}
