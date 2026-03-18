package de.t14d3.trickiertrials;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public final class TrialMobGearGenerator {

    public void apply(LivingEntity entity, double scaledDifficulty, DifficultySettings.MobGearSettings settings) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }

        double gearQuality = DifficultySettings.clamp(
                scaledDifficulty + randomBetween(-settings.qualityJitter(), settings.qualityJitter()),
                0D,
                1D
        );

        applyArmor(equipment, gearQuality, settings);
        applyWeapon(equipment, gearQuality, settings);
    }

    private void applyArmor(EntityEquipment equipment, double gearQuality, DifficultySettings.MobGearSettings settings) {
        if (ThreadLocalRandom.current().nextDouble() >= settings.armorOverallChance().valueAt(gearQuality)) {
            return;
        }

        for (DifficultySettings.ArmorSlot slot : DifficultySettings.ArmorSlot.values()) {
            if (hasArmorInSlot(equipment, slot)) {
                continue;
            }

            if (ThreadLocalRandom.current().nextDouble() >= settings.armorSlotChance(slot).valueAt(gearQuality)) {
                continue;
            }

            Material material = chooseArmorMaterial(slot, gearQuality, settings);
            if (material != null) {
                equipArmor(equipment, slot, material);
            }
        }
    }

    private void applyWeapon(EntityEquipment equipment, double gearQuality, DifficultySettings.MobGearSettings settings) {
        ItemStack mainHand = equipment.getItemInMainHand();
        if (mainHand != null && mainHand.getType() != Material.AIR) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() >= settings.weaponOverallChance().valueAt(gearQuality)) {
            return;
        }

        DifficultySettings.WeaponChoice choice = chooseWeapon(gearQuality, settings);
        if (choice != null) {
            equipment.setItemInMainHand(new ItemStack(choice.material()));
        }
    }

    private Material chooseArmorMaterial(
            DifficultySettings.ArmorSlot slot,
            double gearQuality,
            DifficultySettings.MobGearSettings settings
    ) {
        double totalWeight = 0D;
        for (DifficultySettings.ArmorTier tier : settings.armorTiers()) {
            if (gearQuality < tier.minDifficulty()) {
                continue;
            }

            if (!tier.pieces().containsKey(slot)) {
                continue;
            }

            totalWeight += tier.weight();
        }

        if (totalWeight <= 0D) {
            return null;
        }

        double selection = ThreadLocalRandom.current().nextDouble(totalWeight);
        double runningWeight = 0D;
        for (DifficultySettings.ArmorTier tier : settings.armorTiers()) {
            if (gearQuality < tier.minDifficulty() || !tier.pieces().containsKey(slot)) {
                continue;
            }

            runningWeight += tier.weight();
            if (selection <= runningWeight) {
                return tier.pieces().get(slot);
            }
        }

        return null;
    }

    private DifficultySettings.WeaponChoice chooseWeapon(double gearQuality, DifficultySettings.MobGearSettings settings) {
        double totalWeight = 0D;
        for (DifficultySettings.WeaponChoice choice : settings.weaponChoices()) {
            if (gearQuality >= choice.minDifficulty()) {
                totalWeight += choice.weight();
            }
        }

        if (totalWeight <= 0D) {
            return null;
        }

        double selection = ThreadLocalRandom.current().nextDouble(totalWeight);
        double runningWeight = 0D;
        for (DifficultySettings.WeaponChoice choice : settings.weaponChoices()) {
            if (gearQuality < choice.minDifficulty()) {
                continue;
            }

            runningWeight += choice.weight();
            if (selection <= runningWeight) {
                return choice;
            }
        }

        return null;
    }

    private boolean hasArmorInSlot(EntityEquipment equipment, DifficultySettings.ArmorSlot slot) {
        ItemStack itemStack = switch (slot) {
            case HELMET -> equipment.getHelmet();
            case CHESTPLATE -> equipment.getChestplate();
            case LEGGINGS -> equipment.getLeggings();
            case BOOTS -> equipment.getBoots();
        };
        return itemStack != null && itemStack.getType() != Material.AIR;
    }

    private void equipArmor(EntityEquipment equipment, DifficultySettings.ArmorSlot slot, Material material) {
        ItemStack itemStack = new ItemStack(material);
        switch (slot) {
            case HELMET -> equipment.setHelmet(itemStack);
            case CHESTPLATE -> equipment.setChestplate(itemStack);
            case LEGGINGS -> equipment.setLeggings(itemStack);
            case BOOTS -> equipment.setBoots(itemStack);
        }
    }

    private double randomBetween(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }
}
