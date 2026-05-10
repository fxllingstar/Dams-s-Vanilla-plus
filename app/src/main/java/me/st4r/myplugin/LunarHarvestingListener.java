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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class LunarHarvestingListener implements Listener {

    // Moon phases: 0 = full moon, 4 = new/dark moon. Bukkit returns 0-7.
    private static final int FULL_MOON = 0;
    private static final int DARK_MOON = 4;

    private int getMoonPhase() {
        World overworld = Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(null);
        if (overworld == null) return -1;
        return (int) ((overworld.getFullTime() / 24000L) % 8);
    }

    // -------------------------------------------------------------------------
    // Full Moon: 40% chance to skip an extra growth stage
    // -------------------------------------------------------------------------
    @EventHandler
    public void onCropGrow(BlockGrowEvent event) {
        if (getMoonPhase() != FULL_MOON) return;
        if (!(event.getNewState().getBlockData() instanceof Ageable ageable)) return;
        if (Math.random() >= 0.4) return;

        int next = ageable.getAge() + 1;
        if (next <= ageable.getMaximumAge()) {
            ageable.setAge(next);
            event.getNewState().setBlockData(ageable);
        }
    }

    // -------------------------------------------------------------------------
    // Full Moon: undead spawn weaker (half health)
    // Dark Moon: undead spawn stronger (double health, +10 armor, Resistance + Strength)
    // -------------------------------------------------------------------------
    @EventHandler
    public void onUndeadSpawn(CreatureSpawnEvent event) {
        int phase = getMoonPhase();
        Entity entity = event.getEntity();
        if (!isUndead(entity)) return;
        if (!(entity instanceof LivingEntity living)) return;

        AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        if (phase == FULL_MOON) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() / 2.0);
            living.setHealth(maxHealth.getBaseValue());

        } else if (phase == DARK_MOON) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() * 2.0);
            living.setHealth(maxHealth.getBaseValue());

            AttributeInstance armor = living.getAttribute(Attribute.ARMOR);
            if (armor != null) armor.setBaseValue(armor.getBaseValue() + 10.0);

            living.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            living.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,   Integer.MAX_VALUE, 0, false, false));
        }
    }

    // -------------------------------------------------------------------------
    // Dark Moon: undead drop double loot
    // -------------------------------------------------------------------------
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (getMoonPhase() != DARK_MOON) return;
        if (!isUndead(event.getEntity())) return;

        List<ItemStack> drops = event.getDrops();
        List<ItemStack> bonus = drops.stream()
                .filter(i -> i != null && i.getType() != Material.AIR)
                .map(ItemStack::clone)
                .toList();
        drops.addAll(bonus);
    }

    private boolean isUndead(Entity entity) {
        return entity instanceof Zombie
                || entity instanceof Skeleton
                || entity instanceof Stray
                || entity instanceof WitherSkeleton
                || entity instanceof Drowned
                || entity instanceof Husk
                || entity instanceof Phantom
                || entity instanceof Wither
                || entity instanceof ZombieVillager
                || entity instanceof PigZombie; // ZombifiedPiglin pre-1.16 name kept for compatibility
    }
}
