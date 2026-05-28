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

    private static final int MAX_TRANSFER_SEARCH_BLOCKS = 16384;
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
    private final NamespacedKey gcgModeKey;

    public KineticGridListener() {
        this.sourceBatteryKey = new NamespacedKey("dvplus", "gcg_source_battery");
        this.gcgModeKey = new NamespacedKey("dvplus", "gcg_mode");
    }

    public KineticGridListener(DVPlus plugin) {
        this.sourceBatteryKey = new NamespacedKey(plugin, "gcg_source_battery");
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
        chargeChunkBattery(strikeBlock.getChunk(), chargeToApply);
        sendChargeFeedback(strikeBlock, chargeSource, recipient);
    }

    public double getGridBattery(Chunk currentChunk) {
        return clampBattery(currentChunk.getPersistentDataContainer()
                .getOrDefault(sourceBatteryKey, PersistentDataType.DOUBLE, 0.0));
    }

    public void modifyBattery(Chunk currentChunk, double delta) {
        PersistentDataContainer targetPdc = currentChunk.getPersistentDataContainer();
        double current = targetPdc.getOrDefault(sourceBatteryKey, PersistentDataType.DOUBLE, 0.0);
        targetPdc.set(sourceBatteryKey, PersistentDataType.DOUBLE, clampBattery(current + delta));
    }

    public double transferBattery(Chunk fromChunk, Chunk toChunk) {
        if (fromChunk == null || toChunk == null) return 0.0;
        if (!fromChunk.getWorld().equals(toChunk.getWorld())) return 0.0;
        if (fromChunk.equals(toChunk)) return 0.0;

        if (!containsLightningRod(fromChunk) || !containsLightningRod(toChunk)) return 0.0;
        if (!hasPoweredTransferSignal(toChunk)) return 0.0;
        if (!hasTransferPath(fromChunk, toChunk)) return 0.0;

        double sourceBattery = getGridBattery(fromChunk);
        double targetBattery = getGridBattery(toChunk);
        double transferable = Math.min(sourceBattery, MAX_BATTERY - targetBattery);
        if (transferable <= 0.0) return 0.0;

        modifyBattery(fromChunk, -transferable);
        modifyBattery(toChunk, transferable);
        return transferable;
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

    private void chargeChunkBattery(Chunk chunk, double chargeAmount) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        double currentCharge = pdc.getOrDefault(sourceBatteryKey, PersistentDataType.DOUBLE, 0.0);
        pdc.set(sourceBatteryKey, PersistentDataType.DOUBLE, clampBattery(currentCharge + chargeAmount));
    }

    private boolean containsLightningRod(Chunk chunk) {
        return containsMaterial(chunk, Material.LIGHTNING_ROD);
    }

    private boolean hasPoweredTransferSignal(Chunk chunk) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (!isTransferMaterial(block.getType())) continue;
                    if (block.getBlockPower() > 0) return true;
                }
            }
        }
        return false;
    }

    private boolean hasTransferPath(Chunk fromChunk, Chunk toChunk) {
        Queue<Block> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        boolean foundSeed = false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = fromChunk.getWorld().getMinHeight(); y < fromChunk.getWorld().getMaxHeight(); y++) {
                    Block block = fromChunk.getBlock(x, y, z);
                    if (!isTransferMaterial(block.getType())) continue;
                    if (visited.add(block.getBlockKey())) {
                        queue.add(block);
                        foundSeed = true;
                    }
                }
            }
        }

        if (!foundSeed) return false;

        int explored = 0;
        while (!queue.isEmpty() && explored < MAX_TRANSFER_SEARCH_BLOCKS) {
            Block current = queue.poll();
            explored++;

            if (current.getChunk().equals(toChunk)) {
                return true;
            }

            for (BlockFace face : CONDUCTIVE_FACES) {
                Block neighbor = current.getRelative(face);
                if (!isTransferMaterial(neighbor.getType())) continue;

                if (visited.add(neighbor.getBlockKey())) {
                    queue.add(neighbor);
                }
            }
        }

        return false;
    }

    private boolean containsMaterial(Chunk chunk, Material material) {
        return containsMaterial(chunk, type -> type == material);
    }

    private boolean containsMaterial(Chunk chunk, java.util.function.Predicate<Material> predicate) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
                    Material type = chunk.getBlock(x, y, z).getType();
                    if (predicate.test(type)) return true;
                }
            }
        }
        return false;
    }

    private boolean isTransferMaterial(Material type) {
        return type == Material.LIGHTNING_ROD
                || type == Material.REDSTONE_WIRE
                || type == Material.REDSTONE_BLOCK
                || type == Material.REPEATER
                || type == Material.COMPARATOR
                || isConductiveMaterial(type);
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
