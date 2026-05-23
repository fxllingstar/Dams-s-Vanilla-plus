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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EchoSonarListener implements Listener {

    private static final long CHARGE_TIME_MILLIS = 2_000L;
    private static final long COOLDOWN_MILLIS = 5_000L;
    private static final int PULSE_RADIUS = 15;
    private static final int GLOW_DURATION_TICKS = 100;
    private static final int MAX_USES = 5;
    private static final int MAX_ORE_HIGHLIGHTS = 50;
    private static final Component SONAR_NAME = Component.text("Subterranean Sonar", NamedTextColor.DARK_AQUA);

    private static final Set<Material> SONAR_ORES = EnumSet.of(
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE,
            Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE,
            Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_GOLD_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS
    );

    private final DVPlus plugin;
    private final NamespacedKey sonarUsesKey;
    private final Map<UUID, Long> sneakStartTimes = new HashMap<>();
    private final Map<UUID, Long> cooldownExpiryTimes = new HashMap<>();
    private final Map<UUID, List<UUID>> activeOreHighlights = new HashMap<>();

    public EchoSonarListener(DVPlus plugin) {
        this.plugin = plugin;
        this.sonarUsesKey = new NamespacedKey(plugin, "sonar_uses");
        startSneakCheckTask();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        if (event.isSneaking()) {
            if (!isValidForSonar(player) || isOnCooldown(player.getUniqueId())) {
                return;
            }

            initializeShardIfNeeded(player.getInventory().getItemInOffHand());
            sneakStartTimes.put(player.getUniqueId(), System.currentTimeMillis());
            return;
        }

        sneakStartTimes.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cleanupPlayer(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();
        for (List<UUID> highlightIds : activeOreHighlights.values()) {
            for (UUID entityId : highlightIds) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null) {
                    joiningPlayer.hideEntity(plugin, entity);
                }
            }
        }
    }

    public void shutdown() {
        for (UUID playerId : new ArrayList<>(activeOreHighlights.keySet())) {
            removeOreHighlights(playerId);
        }
        sneakStartTimes.clear();
        cooldownExpiryTimes.clear();
    }

    private void startSneakCheckTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<Map.Entry<UUID, Long>> iterator = sneakStartTimes.entrySet().iterator();
            long now = System.currentTimeMillis();

            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                Player player = Bukkit.getPlayer(entry.getKey());

                if (player == null || !player.isOnline() || !player.isSneaking() || !isValidForSonar(player)) {
                    iterator.remove();
                    continue;
                }

                if (isOnCooldown(player.getUniqueId())) {
                    iterator.remove();
                    continue;
                }

                if (now - entry.getValue() >= CHARGE_TIME_MILLIS) {
                    iterator.remove();
                    triggerPulse(player);
                }
            }
        }, 0L, 4L);
    }

    private boolean isValidForSonar(Player player) {
        return player.getLocation().getBlockY() < 30 && isEchoShard(player.getInventory().getItemInOffHand());
    }

    private boolean isEchoShard(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() == Material.ECHO_SHARD;
    }

    private boolean isOnCooldown(UUID playerId) {
        Long cooldownExpiry = cooldownExpiryTimes.get(playerId);
        if (cooldownExpiry == null) {
            return false;
        }

        if (cooldownExpiry <= System.currentTimeMillis()) {
            cooldownExpiryTimes.remove(playerId);
            return false;
        }

        return true;
    }

    private void triggerPulse(Player player) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (!isEchoShard(offHand)) {
            return;
        }

        initializeShardIfNeeded(offHand);
        if (!consumeCharge(player, offHand)) {
            return;
        }

        cooldownExpiryTimes.put(player.getUniqueId(), System.currentTimeMillis() + COOLDOWN_MILLIS);
        player.sendActionBar(Component.text("Echo pulse released", NamedTextColor.AQUA));

        highlightNearbyMonsters(player);
        highlightNearbyOres(player);
    }

    private void initializeShardIfNeeded(ItemStack offHand) {
        if (!isEchoShard(offHand)) {
            return;
        }

        ItemMeta meta = offHand.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(sonarUsesKey, PersistentDataType.INTEGER)) {
            return;
        }

        pdc.set(sonarUsesKey, PersistentDataType.INTEGER, MAX_USES);
        updateShardLore(meta, MAX_USES);
        offHand.setItemMeta(meta);
    }

    private boolean consumeCharge(Player player, ItemStack offHand) {
        ItemMeta meta = offHand.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int usesRemaining = pdc.getOrDefault(sonarUsesKey, PersistentDataType.INTEGER, MAX_USES) - 1;

        if (usesRemaining > 0) {
            pdc.set(sonarUsesKey, PersistentDataType.INTEGER, usesRemaining);
            updateShardLore(meta, usesRemaining);
            offHand.setItemMeta(meta);
            return true;
        }

        if (offHand.getAmount() > 1) {
            offHand.setAmount(offHand.getAmount() - 1);
            clearShardMetadata(offHand);
        } else {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8F, 0.8F);
        player.sendActionBar(Component.text("Your Echo Shard shattered", NamedTextColor.GRAY));
        return true;
    }

    private void clearShardMetadata(ItemStack offHand) {
        ItemMeta meta = offHand.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.getPersistentDataContainer().remove(sonarUsesKey);
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(this::isSonarLoreLine);
        meta.lore(lore.isEmpty() ? null : lore);
        offHand.setItemMeta(meta);
    }

    private void updateShardLore(ItemMeta meta, int usesRemaining) {
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(this::isSonarLoreLine);
        lore.add(SONAR_NAME);
        lore.add(Component.text("Sonar Charges: " + usesRemaining + "/" + MAX_USES, NamedTextColor.AQUA));
        meta.lore(lore);
    }

    private boolean isSonarLoreLine(Component component) {
        String plainText = PlainTextComponentSerializer.plainText().serialize(component);
        return plainText.equals("Subterranean Sonar") || plainText.startsWith("Sonar Charges:");
    }

    private void highlightNearbyMonsters(Player player) {
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), PULSE_RADIUS, PULSE_RADIUS, PULSE_RADIUS)) {
            if (!(entity instanceof Monster monster)) {
                continue;
            }

            if (monster.getLocation().distanceSquared(player.getLocation()) > PULSE_RADIUS * PULSE_RADIUS) {
                continue;
            }

            monster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, GLOW_DURATION_TICKS, 0, false, false, false));
        }
    }

   private void highlightNearbyOres(Player player) {
        removeOreHighlights(player.getUniqueId());

        World world = player.getWorld();
        int centerX = player.getLocation().getBlockX();
        int centerY = player.getLocation().getBlockY();
        int centerZ = player.getLocation().getBlockZ();
        int radiusSquared = PULSE_RADIUS * PULSE_RADIUS;
        List<UUID> spawnedDisplays = new ArrayList<>();
        Set<Long> highlightedBlocks = new java.util.HashSet<>();

        scanLoop:
        for (int x = -PULSE_RADIUS; x <= PULSE_RADIUS; x++) {
            for (int y = -PULSE_RADIUS; y <= PULSE_RADIUS; y++) {
                for (int z = -PULSE_RADIUS; z <= PULSE_RADIUS; z++) {
                    int distanceSquared = (x * x) + (y * y) + (z * z);
                    if (distanceSquared > radiusSquared) {
                        continue;
                    }

                    Block block = world.getBlockAt(centerX + x, centerY + y, centerZ + z);
                    if (!SONAR_ORES.contains(block.getType())) {
                        continue;
                    }

                    if (hasAdjacentHighlight(block.getX(), block.getY(), block.getZ(), highlightedBlocks)) {
                        continue;
                    }

                    BlockDisplay display = world.spawn(block.getLocation(), BlockDisplay.class, entity -> {
                        entity.setBlock(block.getBlockData());
                        entity.setGlowing(true);
                        entity.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                        entity.setTransformation(new org.bukkit.util.Transformation(
                                new Vector3f(-0.01F, -0.01F, -0.01F),
                                new AxisAngle4f(),
                                new Vector3f(1.02F, 1.02F, 1.02F),
                                new AxisAngle4f()
                        ));
                        entity.setPersistent(false);
                    });

                    for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
                        if (!otherPlayer.getUniqueId().equals(player.getUniqueId())) {
                            otherPlayer.hideEntity(plugin, display);
                        }
                    }

                    spawnedDisplays.add(display.getUniqueId());
                    highlightedBlocks.add(packBlockPos(block.getX(), block.getY(), block.getZ()));

                    if (spawnedDisplays.size() >= MAX_ORE_HIGHLIGHTS) {
                        break scanLoop;
                    }
                }
            }
        }

        if (spawnedDisplays.isEmpty()) {
            return;
        }

        activeOreHighlights.put(player.getUniqueId(), spawnedDisplays);
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeOreHighlights(player.getUniqueId(), spawnedDisplays), GLOW_DURATION_TICKS);
    }

    private void removeOreHighlights(UUID playerId) {
        List<UUID> entityIds = activeOreHighlights.remove(playerId);
        if (entityIds == null) {
            return;
        }
        removeEntities(entityIds);
    }

    private void removeOreHighlights(UUID playerId, List<UUID> entityIds) {
        List<UUID> activeIds = activeOreHighlights.get(playerId);
        if (activeIds == null) {
            removeEntities(entityIds);
            return;
        }

        if (activeIds.equals(entityIds)) {
            activeOreHighlights.remove(playerId);
        }
        removeEntities(entityIds);
    }

    private void removeEntities(List<UUID> entityIds) {
        for (UUID entityId : entityIds) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private boolean hasAdjacentHighlight(int x, int y, int z, Set<Long> highlightedBlocks) {
        return highlightedBlocks.contains(packBlockPos(x + 1, y, z))
                || highlightedBlocks.contains(packBlockPos(x - 1, y, z))
                || highlightedBlocks.contains(packBlockPos(x, y + 1, z))
                || highlightedBlocks.contains(packBlockPos(x, y - 1, z))
                || highlightedBlocks.contains(packBlockPos(x, y, z + 1))
                || highlightedBlocks.contains(packBlockPos(x, y, z - 1));
    }

    private long packBlockPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (y & 0xFFF);
    }

    private void cleanupPlayer(UUID playerId) {
        sneakStartTimes.remove(playerId);
        cooldownExpiryTimes.remove(playerId);
        removeOreHighlights(playerId);
    }
}
