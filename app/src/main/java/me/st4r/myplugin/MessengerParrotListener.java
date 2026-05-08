package me.st4r.myplugin;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class MessengerParrotListener implements Listener, CommandExecutor {

    private final DVPlus plugin;

    // Parrot UUID -> the item it's carrying
    private final Map<UUID, ItemStack> parrotPayload = new HashMap<>();
    // Parrot UUID -> sender player UUID
    private final Map<UUID, UUID> parrotSender = new HashMap<>();
    // Parrot UUID -> target player name
    private final Map<UUID, String> parrotTarget = new HashMap<>();
    // Parrot UUID -> flying task
    private final Map<UUID, BukkitTask> parrotTask = new HashMap<>();
    // Player UUID -> pending parrot (waiting for /parrot deliver within 60s)
    private final Map<UUID, UUID> pendingDelivery = new HashMap<>();
    // Player UUID -> timeout task for the 60s window
    private final Map<UUID, BukkitTask> pendingTimeout = new HashMap<>();
    // Parrot UUID -> whether it's currently a messenger parrot
    private final Set<UUID> messengerParrots = new HashSet<>();

    public MessengerParrotListener(DVPlus plugin) {
        this.plugin = plugin;
    }

    // Step 1: Player right-clicks their tamed parrot with a Book and Quill
    @EventHandler
    public void onInteractParrot(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Parrot parrot)) return;
        if (!parrot.isTamed()) return;
        if (!parrot.getOwner().getUniqueId().equals(event.getPlayer().getUniqueId())) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (held.getType() != Material.WRITABLE_BOOK) return;

        event.setCancelled(true);

        // Check the parrot isn't already a messenger
        if (messengerParrots.contains(parrot.getUniqueId())) {
            player.sendMessage("§eThis parrot is already a messenger parrot.");
            return;
        }

        // Give the parrot the book, mark it as pending delivery selection
        parrotPayload.put(parrot.getUniqueId(), held.clone());
        held.setAmount(held.getAmount() - 1);

        pendingDelivery.put(player.getUniqueId(), parrot.getUniqueId());

        player.sendMessage("§aYou've given your parrot a Book and Quill!");
        player.sendMessage("§eUse §f/parrot deliver <PlayerName> §ewithin §f60 seconds §eto send it.");

        // 60-second timeout — return book if no target selected
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingDelivery.containsKey(player.getUniqueId())) {
                pendingDelivery.remove(player.getUniqueId());
                ItemStack book = parrotPayload.remove(parrot.getUniqueId());
                if (book != null) {
                    player.getInventory().addItem(book);
                    player.sendMessage("§cYour parrot returned the book — no target was selected in time.");
                }
            }
        }, 1200L); // 60 seconds

        pendingTimeout.put(player.getUniqueId(), timeout);
    }

    // Step 2: /parrot deliver <PlayerName>
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("deliver")) {
            player.sendMessage("§cUsage: /parrot deliver <PlayerName>");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /parrot deliver <PlayerName>");
            return true;
        }

        UUID parrotId = pendingDelivery.get(player.getUniqueId());
        if (parrotId == null) {
            player.sendMessage("§cYou don't have a parrot ready to deliver. Right-click your parrot with a Book and Quill first.");
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage("§cPlayer §f" + targetName + " §cis not online.");
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage("§cYou can't send a message to yourself.");
            return true;
        }

        // Cancel the timeout
        BukkitTask t = pendingTimeout.remove(player.getUniqueId());
        if (t != null) t.cancel();
        pendingDelivery.remove(player.getUniqueId());

        // Find the parrot entity
        Entity parrotEntity = Bukkit.getEntity(parrotId);
        if (!(parrotEntity instanceof Parrot parrot)) {
            player.sendMessage("§cCouldn't find your parrot!");
            parrotPayload.remove(parrotId);
            return true;
        }

        // Activate messenger mode
        messengerParrots.add(parrotId);
        parrotSender.put(parrotId, player.getUniqueId());
        parrotTarget.put(parrotId, target.getName());

        player.sendMessage("§aYour parrot is on its way to §f" + target.getName() + "§a!");
        launchParrotToTarget(parrot, target);
        return true;
    }

    private void launchParrotToTarget(Parrot parrot, Player target) {
        UUID parrotId = parrot.getUniqueId();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Parrot or target may have logged off / died
            Entity pe = Bukkit.getEntity(parrotId);
            if (!(pe instanceof Parrot p)) {
                cleanup(parrotId, false);
                return;
            }

            Player dest = Bukkit.getPlayerExact(parrotTarget.getOrDefault(parrotId, ""));
            if (dest == null || !dest.isOnline()) {
                returnParrotToOwner(p);
                return;
            }

            // Move toward target at 2x normal parrot speed
            Location parrotLoc = p.getLocation();
            Location targetLoc = dest.getLocation().add(0, 1.5, 0);

            if (parrotLoc.getWorld() != targetLoc.getWorld()) return;

            double distance = parrotLoc.distance(targetLoc);

            if (distance <= 1.5) {
                // Arrived — deliver the item
                deliverToTarget(p, dest);
                return;
            }

            Vector direction = targetLoc.toVector().subtract(parrotLoc.toVector()).normalize().multiply(0.6);
            p.setVelocity(direction);
            p.teleport(parrotLoc.setDirection(direction));

        }, 0L, 2L); // Every 2 ticks (~10 times/sec)

        parrotTask.put(parrotId, task);
    }

    private void deliverToTarget(Parrot parrot, Player target) {
        UUID parrotId = parrot.getUniqueId();
        UUID senderUUID = parrotSender.get(parrotId);
        Player senderPlayer = Bukkit.getPlayer(senderUUID);

        String senderName = senderPlayer != null ? senderPlayer.getName() : "Unknown";

        target.sendMessage("§6§lMessenger Parrot! §r§e" + senderName + " §7sent you a message:");
        target.sendMessage("§7(Right-click the parrot to receive it, then §f/parrot reply §7to respond)");
        parrot.teleport(target.getLocation().add(0, 1.5, 0));

        // Notify the sender
        if (senderPlayer != null) {
            senderPlayer.sendMessage("§aYour parrot delivered the message to §f" + target.getName() + "§a!");
        }

        // Cancel flying task
        BukkitTask t = parrotTask.remove(parrotId);
        if (t != null) t.cancel();

        // Wait for the recipient to right-click the parrot to receive the book
        // Store a pending reply state
        parrotTarget.put(parrotId, target.getName());
        // Schedule parrot return if no reply in 2 minutes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (messengerParrots.contains(parrotId)) {
                returnParrotToOwner(parrot);
            }
        }, 2400L); // 2 minutes
    }

    // Recipient right-clicks the parrot to receive the book, or parrot returns to owner
    @EventHandler
    public void onRecipientInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Parrot parrot)) return;
        UUID parrotId = parrot.getUniqueId();
        if (!messengerParrots.contains(parrotId)) return;

        Player player = event.getPlayer();
        String targetName = parrotTarget.get(parrotId);
        if (targetName == null || !player.getName().equals(targetName)) return;

        event.setCancelled(true);

        // Give the payload to the recipient
        ItemStack payload = parrotPayload.get(parrotId);
        if (payload != null) {
            player.getInventory().addItem(payload);
            player.sendMessage("§aYou received a message! Use §f/parrot reply §ato send a reply.");
        }

        // Mark parrot as waiting for reply — give recipient 2 minutes
        // (If they want to reply, they use /parrot reply — future enhancement)
        // For now, parrot returns to owner after delivery
        returnParrotToOwner(parrot);
    }

    private void returnParrotToOwner(Parrot parrot) {
        UUID parrotId = parrot.getUniqueId();
        UUID senderUUID = parrotSender.get(parrotId);
        if (senderUUID == null) {
            cleanup(parrotId, false);
            return;
        }

        Player owner = Bukkit.getPlayer(senderUUID);
        if (owner == null || !owner.isOnline()) {
            cleanup(parrotId, false);
            return;
        }

        // Fly back to owner
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Entity pe = Bukkit.getEntity(parrotId);
            if (!(pe instanceof Parrot p)) {
                cleanup(parrotId, false);
                return;
            }
            Player o = Bukkit.getPlayer(senderUUID);
            if (o == null || !o.isOnline()) {
                cleanup(parrotId, false);
                return;
            }

            Location parrotLoc = p.getLocation();
            Location ownerLoc = o.getLocation().add(0, 1.5, 0);

            if (parrotLoc.getWorld() != ownerLoc.getWorld()) return;

            if (parrotLoc.distance(ownerLoc) <= 1.5) {
                o.sendMessage("§aYour parrot has returned!");
                cleanup(parrotId, false);
                return;
            }

            Vector direction = ownerLoc.toVector().subtract(parrotLoc.toVector()).normalize().multiply(0.6);
            p.setVelocity(direction);

        }, 0L, 2L);

        BukkitTask old = parrotTask.put(parrotId, task);
        if (old != null) old.cancel();
    }

    private void cleanup(UUID parrotId, boolean keepPayload) {
        BukkitTask t = parrotTask.remove(parrotId);
        if (t != null) t.cancel();
        messengerParrots.remove(parrotId);
        parrotSender.remove(parrotId);
        parrotTarget.remove(parrotId);
        if (!keepPayload) parrotPayload.remove(parrotId);
    }

    // Prevent messenger parrots from taking damage mid-flight
    @EventHandler
    public void onParrotDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Parrot parrot)) return;
        if (messengerParrots.contains(parrot.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
