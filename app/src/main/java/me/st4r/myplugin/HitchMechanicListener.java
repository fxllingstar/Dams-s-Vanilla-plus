/*
 * DVPlus (Dams's Vanilla+)
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

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HitchMechanicListener implements Listener {

    private final DVPlus plugin;

    // Horse UUID -> hitched vehicle (Boat or Minecart)
    private final Map<UUID, UUID> hitchedVehicle = new HashMap<>();
    // Horse UUID -> drag task
    private final Map<UUID, BukkitTask> dragTasks = new HashMap<>();
    // Horse UUID -> leash knot used for the visual lead
    private final Map<UUID, UUID> leashKnots = new HashMap<>();

    public HitchMechanicListener(DVPlus plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity clicked = event.getRightClicked();

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (clicked instanceof AbstractHorse horse) {
            if (!horse.isTamed()) return;

            // Unhitch if already hitched (no lead required to untangle)
            if (hitchedVehicle.containsKey(horse.getUniqueId())) {
                event.setCancelled(true);
                unhitch(horse);
                player.sendMessage("§eUnhitched the cargo from your horse.");
                return;
            }

            // Hitch requires a lead in hand
            if (hand.getType() != Material.LEAD) return;

            Entity nearby = findNearbyHitchable(horse.getLocation(), 4.0);
            if (nearby == null) {
                player.sendMessage("§cNo boat or minecart nearby to hitch. Get within 4 blocks of one.");
                return;
            }

            event.setCancelled(true);

            // Consume one lead from hand
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }

            hitchedVehicle.put(horse.getUniqueId(), nearby.getUniqueId());

            // Spawn a LeashHitch at the cargo location so the horse's leash renders visually
            LeashHitch knot = nearby.getWorld().spawn(nearby.getLocation(), LeashHitch.class);
            horse.setLeashHolder(knot);
            leashKnots.put(horse.getUniqueId(), knot.getUniqueId());

            player.sendMessage("§aHitched! Your horse will drag the " + getFriendlyName(nearby) + ".");
            startDragTask(horse, nearby);
        }

        // Right-clicking the cargo with a lead also unhitches
        if (clicked instanceof Boat || clicked instanceof Minecart) {
            if (hand.getType() != Material.LEAD) return;
            for (Map.Entry<UUID, UUID> entry : hitchedVehicle.entrySet()) {
                if (entry.getValue().equals(clicked.getUniqueId())) {
                    Entity horseEntity = Bukkit.getEntity(entry.getKey());
                    if (horseEntity instanceof AbstractHorse horse) {
                        event.setCancelled(true);
                        unhitch(horse);
                        player.sendMessage("§eUnhitched the cargo from the horse.");
                    }
                    return;
                }
            }
        }
    }

    private void unhitch(AbstractHorse horse) {
        UUID horseId = horse.getUniqueId();
        hitchedVehicle.remove(horseId);

        // Remove the leash from the horse and kill the knot entity
        if (horse.isLeashed()) horse.setLeashHolder(null);
        UUID knotId = leashKnots.remove(horseId);
        if (knotId != null) {
            Entity knot = Bukkit.getEntity(knotId);
            if (knot != null) knot.remove();
        }

        BukkitTask task = dragTasks.remove(horseId);
        if (task != null) task.cancel();
    }

    private void startDragTask(AbstractHorse horse, Entity cargo) {
        UUID horseId = horse.getUniqueId();
        UUID cargoId = cargo.getUniqueId();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Entity h = Bukkit.getEntity(horseId);
            Entity c = Bukkit.getEntity(cargoId);

            if (h == null || c == null || !hitchedVehicle.containsKey(horseId)) {
                if (h instanceof AbstractHorse ah) unhitch(ah);
                else hitchedVehicle.remove(horseId);
                return;
            }

            if (!hitchedVehicle.get(horseId).equals(cargoId)) return;

            Location horseLoc = h.getLocation();
            Location cargoLoc = c.getLocation();

            if (horseLoc.distance(cargoLoc) > 3.0) {
                org.bukkit.util.Vector pull = horseLoc.toVector()
                        .subtract(cargoLoc.toVector())
                        .normalize()
                        .multiply(0.35);
                c.setVelocity(pull);
            }

        }, 0L, 1L);

        dragTasks.put(horseId, task);
    }

    private Entity findNearbyHitchable(Location loc, double radius) {
        for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof Boat || e instanceof Minecart) return e;
        }
        return null;
    }

    private String getFriendlyName(Entity e) {
        if (e instanceof Boat) return "boat";
        if (e instanceof Minecart) return "minecart";
        return "vehicle";
    }
}
