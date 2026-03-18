package de.t14d3.trickiertrials;

import org.apache.commons.lang3.RandomUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Breeze;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.TrialSpawnerSpawnEvent;
import org.bukkit.persistence.PersistentDataType;

public class TrialSpawnerListener implements Listener {

    private static final NamespacedKey TRIAL_SPAWNED_KEY = new NamespacedKey("trickiertrials", "trialspawned");

    private final TrickierTrials plugin;
    private final TrialDifficultyCalculator difficultyCalculator;
    private final PlayerDifficultyRepository playerDifficultyRepository;
    private final TrialMobGearGenerator gearGenerator;

    public TrialSpawnerListener(
            TrickierTrials plugin,
            TrialDifficultyCalculator difficultyCalculator,
            PlayerDifficultyRepository playerDifficultyRepository,
            TrialMobGearGenerator gearGenerator
    ) {
        this.plugin = plugin;
        this.difficultyCalculator = difficultyCalculator;
        this.playerDifficultyRepository = playerDifficultyRepository;
        this.gearGenerator = gearGenerator;
    }

    public void healthMultiplier(LivingEntity entity, double healthMultiplier) {
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        maxHealth.setBaseValue(maxHealth.getBaseValue() * healthMultiplier);
        entity.setHealth(maxHealth.getValue());
    }

    public void damageMultiplier(LivingEntity entity, double damageMultiplier) {
        AttributeInstance attackDamage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return;
        }

        attackDamage.setBaseValue(attackDamage.getBaseValue() * damageMultiplier);
    }

    @EventHandler
    public void onSpawn(TrialSpawnerSpawnEvent event) {
        if (!plugin.isStrengthenTrialMobs()) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }

        TrialDifficultyCalculator.DifficultyResult difficulty = difficultyCalculator.calculate(
                event.getTrialSpawner().getTrackedPlayers(),
                plugin.getDifficultySettings(),
                playerDifficultyRepository
        );

        if (plugin.isGlowingEffect()) {
            entity.setGlowing(true);
        }

        if (difficulty.healthMultiplier() > 1D) {
            healthMultiplier(entity, difficulty.healthMultiplier());
        }
        if (difficulty.damageMultiplier() > 1D) {
            damageMultiplier(entity, difficulty.damageMultiplier());
        }

        gearGenerator.apply(entity, difficulty.scaledDifficulty(), plugin.getDifficultySettings().mobGear());

        entity.getPersistentDataContainer().set(TRIAL_SPAWNED_KEY, PersistentDataType.INTEGER, 1);

        // Easter Egg
        if (plugin.isSecret() && entity instanceof Breeze && RandomUtils.nextDouble(0, 1) < 0.05) {
            entity.setCustomName(plugin.getSecretName());
            entity.setCustomNameVisible(true);
            healthMultiplier(entity, 4);

            AttributeInstance movementSpeed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
            if (movementSpeed != null) {
                movementSpeed.setBaseValue(1.2);
            }
        }
    }
}