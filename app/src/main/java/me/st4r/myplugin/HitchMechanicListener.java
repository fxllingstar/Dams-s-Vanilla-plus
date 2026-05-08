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
    // Horse UUID -> drag task, so we can cancel it on unhitch
    private final Map<UUID, BukkitTask> dragTasks = new HashMap<>();

    public HitchMechanicListener(DVPlus plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity clicked = event.getRightClicked();

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.LEAD) return;

        if (clicked instanceof AbstractHorse horse) {
            if (!horse.isTamed()) {
                player.sendMessage("§cYou can only hitch tamed horses.");
                return;
            }

            UUID hitchedId = hitchedVehicle.get(horse.getUniqueId());
            if (hitchedId != null) {
                unhitch(horse.getUniqueId());
                player.sendMessage("§eUnhitched the cargo from your horse.");
                event.setCancelled(true);
                return;
            }

            Entity nearby = findNearbyHitchable(horse.getLocation(), 4.0);
            if (nearby == null) {
                player.sendMessage("§cNo boat or minecart nearby to hitch. Get within 4 blocks of one.");
                return;
            }

            event.setCancelled(true);
            hitchedVehicle.put(horse.getUniqueId(), nearby.getUniqueId());
            player.sendMessage("§aHitched! Your horse will drag the " + getFriendlyName(nearby) + ".");
            startDragTask(horse, nearby);
        }

        // Right-clicking the boat/minecart while holding a lead — unhitch
        if (clicked instanceof Boat || clicked instanceof Minecart) {
            for (Map.Entry<UUID, UUID> entry : hitchedVehicle.entrySet()) {
                if (entry.getValue().equals(clicked.getUniqueId())) {
                    unhitch(entry.getKey());
                    player.sendMessage("§eUnhitched the cargo from the horse.");
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private void unhitch(UUID horseId) {
        hitchedVehicle.remove(horseId);
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
                unhitch(horseId);
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
