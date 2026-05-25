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
import org.bukkit.ChatColor;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
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
    private static final int PULSE_RADIUS = 20;
    private static final int GLOW_DURATION_TICKS = 200;
    private static final int MAX_USES = 3;
    private static final int MAX_ORE_HIGHLIGHTS = 50;
    private static final Component SONAR_NAME = Component.text("Subterranean Sonar", NamedTextColor.DARK_AQUA);
    private static final String SONAR_GLOW_TEAM_NAME = "dvplus_sonar_glow";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_VERBOSE = false;

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
        debug("EchoSonarListener initialized.");
        startSneakCheckTask();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        debug(player, "onSneak fired. Sneaking=" + event.isSneaking());

        if (event.isSneaking()) {
            if (!isValidForSonar(player) || isOnCooldown(player.getUniqueId())) {
                debug(player, "Sneak start ignored (invalid sonar state or cooldown active).");
                return;
            }

            initializeShardIfNeeded(player.getInventory().getItemInOffHand());
            sneakStartTimes.put(player.getUniqueId(), System.currentTimeMillis());
            debug(player, "Charge start timestamp stored.");
            return;
        }

        sneakStartTimes.remove(player.getUniqueId());
        debug(player, "Sneak ended; pending charge removed.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        debug(event.getPlayer(), "Player quit; cleaning sonar state.");
        cleanupPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        debug(event.getEntity(), "Player died; cleaning sonar state.");
        cleanupPlayer(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();
        debug(joiningPlayer, "Player joined; hiding active ore highlight displays.");
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
        debug("Shutdown called; clearing active highlights and caches.");
        for (UUID playerId : new ArrayList<>(activeOreHighlights.keySet())) {
            removeOreHighlights(playerId);
        }
        sneakStartTimes.clear();
        cooldownExpiryTimes.clear();
    }

   private void startSneakCheckTask() {
    debug("Starting sneak charge monitor task.");
    Bukkit.getScheduler().runTaskTimer(plugin, () -> {
        Iterator<Map.Entry<UUID, Long>> iterator = sneakStartTimes.entrySet().iterator();
        long now = System.currentTimeMillis();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());

            if (player == null || !player.isOnline() || !player.isSneaking() || !isValidForSonar(player)) {
                debug(entry.getKey(), "Charge tracking removed (offline/not sneaking/invalid state).");
                // Clear the action bar if they stop crouching early
                if (player != null && player.isOnline()) {
                    player.sendActionBar(Component.text("Charge cancelled", NamedTextColor.RED));
                }
                iterator.remove();
                continue;
            }

            if (isOnCooldown(player.getUniqueId())) {
                debug(player, "Charge tracking removed (cooldown active).");
                iterator.remove();
                continue;
            }

            long elapsed = now - entry.getValue();

            if (elapsed >= CHARGE_TIME_MILLIS) {
                iterator.remove();
                debug(player, "Charge time reached; triggering pulse.");
                triggerPulse(player);
            } else {
                double remainingSeconds = (CHARGE_TIME_MILLIS - elapsed) / 1000.0;
                int progressBlocks = (int) ((double) elapsed / CHARGE_TIME_MILLIS * 10);
                String progressBar = "█".repeat(progressBlocks) + "░".repeat(10 - progressBlocks);

                Component chargeMessage = Component.text("Sonar Charging: ", NamedTextColor.DARK_AQUA)
                        .append(Component.text(String.format("%.1fs ", remainingSeconds), NamedTextColor.AQUA))
                        .append(Component.text("[" + progressBar + "]", NamedTextColor.GRAY));

                player.sendActionBar(chargeMessage);
            }
        }
    }, 0L, 4L);
}

   private boolean isValidForSonar(Player player) {
    ItemStack mainHand = player.getInventory().getItemInMainHand();
    ItemStack offHand = player.getInventory().getItemInOffHand();
    
    // Check if either hand contains a valid Echo Shard
    boolean hasShard = isEchoShard(mainHand) || isEchoShard(offHand);
    
    boolean valid = player.getLocation().getBlockY() < 30 && hasShard;
    
    if (DEBUG_VERBOSE) {
        debug(player, "isValidForSonar=" + valid + ", y=" + player.getLocation().getBlockY());
    }
    return valid;
}

    private boolean isEchoShard(ItemStack itemStack) {
        boolean isShard = itemStack != null && itemStack.getType() == Material.ECHO_SHARD;
        if (DEBUG_VERBOSE) {
            debug("isEchoShard=" + isShard);
        }
        return isShard;
    }

    private boolean isOnCooldown(UUID playerId) {
        Long cooldownExpiry = cooldownExpiryTimes.get(playerId);
        if (cooldownExpiry == null) {
            return false;
        }

        if (cooldownExpiry <= System.currentTimeMillis()) {
            cooldownExpiryTimes.remove(playerId);
            debug(playerId, "Cooldown expired and cleared.");
            return false;
        }

        if (DEBUG_VERBOSE) {
            debug(playerId, "Cooldown active until " + cooldownExpiry + ".");
        }
        return true;
    }
private void triggerPulse(Player player) {
    debug(player, "triggerPulse called.");
    ItemStack sonarItem = player.getInventory().getItemInMainHand();
    if (!isEchoShard(sonarItem)) {
        sonarItem = player.getInventory().getItemInOffHand();
    }

    if (!isEchoShard(sonarItem)) {
        debug(player, "Pulse aborted: No echo shard found in either hand.");
        return;
    }

    initializeShardIfNeeded(sonarItem);
    if (!consumeCharge(player, sonarItem)) {
        debug(player, "Pulse aborted: failed to consume charge.");
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
            if (DEBUG_VERBOSE) {
                debug("Shard already initialized.");
            }
            return;
        }

        pdc.set(sonarUsesKey, PersistentDataType.INTEGER, MAX_USES);
        updateShardLore(meta, MAX_USES);
        offHand.setItemMeta(meta);
        debug("Shard initialized with max sonar uses.");
    }

    private boolean consumeCharge(Player player, ItemStack offHand) {
        ItemMeta meta = offHand.getItemMeta();
        if (meta == null) {
            debug(player, "consumeCharge failed: missing item meta.");
            return false;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int usesRemaining = pdc.getOrDefault(sonarUsesKey, PersistentDataType.INTEGER, MAX_USES) - 1;
        debug(player, "consumeCharge: usesRemaining after consume=" + usesRemaining);

        if (usesRemaining > 0) {
            pdc.set(sonarUsesKey, PersistentDataType.INTEGER, usesRemaining);
            updateShardLore(meta, usesRemaining);
            offHand.setItemMeta(meta);
            debug(player, "Charge consumed successfully; shard updated.");
            return true;
        }

        if (offHand.getAmount() > 1) {
            offHand.setAmount(offHand.getAmount() - 1);
            clearShardMetadata(offHand);
            debug(player, "Shard stack decremented; metadata cleared for next shard.");
        } else {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            debug(player, "Last shard consumed; off-hand cleared.");
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
        debug("Shard metadata and sonar lore cleared.");
    }

    private void updateShardLore(ItemMeta meta, int usesRemaining) {
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(this::isSonarLoreLine);
        lore.add(SONAR_NAME);
        lore.add(Component.text("Sonar Charges: " + usesRemaining + "/" + MAX_USES, NamedTextColor.AQUA));
        meta.lore(lore);
        if (DEBUG_VERBOSE) {
            debug("Shard lore updated. Uses remaining=" + usesRemaining);
        }
    }

    private boolean isSonarLoreLine(Component component) {
        String plainText = PlainTextComponentSerializer.plainText().serialize(component);
        return plainText.equals("Subterranean Sonar") || plainText.startsWith("Sonar Charges:");
    }

    private void highlightNearbyMonsters(Player player) {
        int highlightedMonsters = 0;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), PULSE_RADIUS, PULSE_RADIUS, PULSE_RADIUS)) {
            if (!(entity instanceof Monster monster)) {
                continue;
            }

            if (monster.getLocation().distanceSquared(player.getLocation()) > PULSE_RADIUS * PULSE_RADIUS) {
                continue;
            }

            monster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, GLOW_DURATION_TICKS, 0, false, false, false));
            highlightedMonsters++;
        }
        debug(player, "Highlighted monsters=" + highlightedMonsters);
    }

   private void highlightNearbyOres(Player player) {
        debug(player, "highlightNearbyOres started.");
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

                    registerGlowTeam(display);
                    player.showEntity(plugin, display);

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
            debug(player, "No ores qualified for highlighting.");
            return;
        }

        activeOreHighlights.put(player.getUniqueId(), spawnedDisplays);
        debug(player, "Spawned ore highlights=" + spawnedDisplays.size());
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeOreHighlights(player.getUniqueId(), spawnedDisplays), GLOW_DURATION_TICKS);
    }

    private void removeOreHighlights(UUID playerId) {
        List<UUID> entityIds = activeOreHighlights.remove(playerId);
        if (entityIds == null) {
            if (DEBUG_VERBOSE) {
                debug(playerId, "No active ore highlights to remove.");
            }
            return;
        }
        debug(playerId, "Removing active ore highlights=" + entityIds.size());
        removeEntities(entityIds);
    }

    private void removeOreHighlights(UUID playerId, List<UUID> entityIds) {
        List<UUID> activeIds = activeOreHighlights.get(playerId);
        if (activeIds == null) {
            if (DEBUG_VERBOSE) {
                debug(playerId, "No active id set; removing provided highlight ids=" + entityIds.size());
            }
            removeEntities(entityIds);
            return;
        }

        if (activeIds.equals(entityIds)) {
            activeOreHighlights.remove(playerId);
            if (DEBUG_VERBOSE) {
                debug(playerId, "Scheduled highlight ids match active set; removed from tracking.");
            }
        }
        removeEntities(entityIds);
    }

    private void removeEntities(List<UUID> entityIds) {
        int removed = 0;
        for (UUID entityId : entityIds) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
                removed++;
            }
            unregisterGlowTeam(entityId);
        }
        if (DEBUG_VERBOSE) {
            debug("removeEntities completed. Removed=" + removed + "/" + entityIds.size());
        }
    }

    private long packBlockPos(int x, int y, int z) {
        int normalizedY = y + 64;
        long packed = ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (normalizedY & 0xFFF);
        if (DEBUG_VERBOSE) {
            debug("packBlockPos(" + x + "," + y + "," + z + ")=" + packed);
        }
        return packed;
    }

    private boolean hasAdjacentHighlight(int x, int y, int z, Set<Long> highlightedBlocks) {
        boolean adjacent = highlightedBlocks.contains(packBlockPos(x + 1, y, z))
                || highlightedBlocks.contains(packBlockPos(x - 1, y, z))
                || highlightedBlocks.contains(packBlockPos(x, y + 1, z))
                || highlightedBlocks.contains(packBlockPos(x, y - 1, z))
                || highlightedBlocks.contains(packBlockPos(x, y, z + 1))
                || highlightedBlocks.contains(packBlockPos(x, y, z - 1));
        if (DEBUG_VERBOSE && adjacent) {
            debug("Adjacent highlight detected at " + x + "," + y + "," + z);
        }
        return adjacent;
    }

    private void cleanupPlayer(UUID playerId) {
        sneakStartTimes.remove(playerId);
        cooldownExpiryTimes.remove(playerId);
        removeOreHighlights(playerId);
        debug(playerId, "Player sonar state cleaned.");
    }

    private void registerGlowTeam(BlockDisplay display) {
        Team team = getOrCreateGlowTeam();
        if (team == null) {
            return;
        }

        String entry = display.getUniqueId().toString();
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    private void unregisterGlowTeam(UUID entityId) {
        Team team = getOrCreateGlowTeam();
        if (team == null) {
            return;
        }

        String entry = entityId.toString();
        if (team.hasEntry(entry)) {
            team.removeEntry(entry);
        }
    }

    private Team getOrCreateGlowTeam() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            debug("ScoreboardManager unavailable; skipping glow team assignment.");
            return null;
        }

        Scoreboard scoreboard = manager.getMainScoreboard();
        Team team = scoreboard.getTeam(SONAR_GLOW_TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(SONAR_GLOW_TEAM_NAME);
            team.setColor(ChatColor.DARK_AQUA);
            team.setCanSeeFriendlyInvisibles(false);
            if (DEBUG_VERBOSE) {
                debug("Created sonar glow scoreboard team.");
            }
        }

        return team;
    }

    private void debug(Player player, String message) {
        debug(player.getUniqueId(), player.getName() + ": " + message);
    }

    private void debug(UUID playerId, String message) {
        debug("[player=" + playerId + "] " + message);
    }

    private void debug(String message) {
        if (!DEBUG) {
            return;
        }
        plugin.getLogger().info("[EchoSonar Debug] " + message);
    }
}


