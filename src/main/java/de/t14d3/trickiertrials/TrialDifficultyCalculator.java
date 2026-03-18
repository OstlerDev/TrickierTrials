package de.t14d3.trickiertrials;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

public final class TrialDifficultyCalculator {

    public DifficultyResult calculate(
            Collection<? extends Player> trackedPlayers,
            DifficultySettings settings,
            PlayerDifficultyRepository playerDifficultyRepository
    ) {
        if (trackedPlayers.isEmpty()) {
            return DifficultyResult.vanilla();
        }

        double totalContribution = 0D;
        for (Player player : trackedPlayers) {
            totalContribution += calculatePlayerContribution(player, settings, playerDifficultyRepository.getPoints(player.getUniqueId()));
        }

        double meanContribution = totalContribution / trackedPlayers.size();
        double normalizedDifficulty = clamp(meanContribution / settings.combat().scoreAtMaxDifficulty(), 0D, 1D);
        double scaledDifficulty = clamp(normalizedDifficulty * settings.scale(), 0D, 1D);
        double healthMultiplier = 1D + (scaledDifficulty * (settings.combat().maxHealthMultiplier() - 1D));
        double damageMultiplier = 1D + (scaledDifficulty * (settings.combat().maxDamageMultiplier() - 1D));

        return new DifficultyResult(
                meanContribution,
                scaledDifficulty,
                healthMultiplier,
                damageMultiplier
        );
    }

    private double calculatePlayerContribution(Player player, DifficultySettings settings, int progressionPoints) {
        if (progressionPoints <= 0) {
            return 0D;
        }

        double readinessScore = calculateReadinessScore(player, settings);
        double progressionLevel = Math.min(progressionPoints / settings.progression().pointsPerLevel(), settings.progression().maxLevel());
        return readinessScore + (progressionLevel * settings.progression().scoreBonusPerLevel());
    }

    private double calculateReadinessScore(Player player, DifficultySettings settings) {
        double score = 0D;
        ItemStack[] armorContents = player.getEquipment().getArmorContents();
        for (ItemStack armorPiece : armorContents) {
            if (armorPiece != null) {
                score += settings.readiness().armorScore(armorPiece.getType());
            }
        }

        ItemStack mainHand = player.getEquipment().getItemInMainHand();
        if (mainHand != null) {
            score += settings.readiness().weaponScore(mainHand.getType());
        }

        ItemStack offHand = player.getEquipment().getItemInOffHand();
        if (offHand != null) {
            score += settings.readiness().weaponScore(offHand.getType());
        }

        return score;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record DifficultyResult(
            double meanContribution,
            double scaledDifficulty,
            double healthMultiplier,
            double damageMultiplier
    ) {
        public static DifficultyResult vanilla() {
            return new DifficultyResult(0D, 0D, 1D, 1D);
        }
    }
}
