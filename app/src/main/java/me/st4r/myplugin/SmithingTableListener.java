package me.st4r.myplugin;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public class SmithingTableListener implements Listener {

    private static final int MAX_LUMINOUS_TIME = 12000;

    // Slot layout in 1.20+: 0 = template, 1 = base, 2 = addition
    // We don't require a template — slot 0 can be anything or empty.
    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();

        // Try both legacy (slot 0 = base, slot 1 = addition) and
        // modern (slot 0 = template, slot 1 = base, slot 2 = addition) layouts.
        ItemStack base = null;
        ItemStack addition = null;

        for (int size = inv.getSize(), i = 0; i < size; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null) continue;
            if (slot.getType() == Material.GLOW_INK_SAC) {
                addition = slot;
            } else if (isValidLuminousItem(slot.getType())) {
                base = slot;
            }
        }

        if (base == null || addition == null) return;

        ItemStack result = base.clone();
        var meta = result.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().set(DVPlus.LUMINOUS_KEY, PersistentDataType.INTEGER, MAX_LUMINOUS_TIME);

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line).contains("Luminous"));
        lore.add(Component.text("✦ Luminous", NamedTextColor.AQUA));
        meta.lore(lore);
        result.setItemMeta(meta);

        event.setResult(result);
    }

    private boolean isValidLuminousItem(Material type) {
        String name = type.toString();
        return name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS")
            || name.endsWith("_BOOTS")
            || name.endsWith("_SWORD")
            || name.endsWith("_PICKAXE")
            || name.endsWith("_AXE")
            || name.endsWith("_SHOVEL")
            || name.endsWith("_HOE");
    }
}