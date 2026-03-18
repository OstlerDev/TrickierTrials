package de.t14d3.trickiertrials;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

public class TrialVaultRefresher implements Listener {

    private final TrickierTrials plugin;
    private final TrialVaultResetManager manager;
    private final VaultAccess vaultAccess;
    private final PlayerDifficultyRepository playerDifficultyRepository;

    public TrialVaultRefresher(
            TrickierTrials plugin,
            TrialVaultResetManager manager,
            VaultAccess vaultAccess,
            PlayerDifficultyRepository playerDifficultyRepository
    ) {
        this.plugin = plugin;
        this.manager = manager;
        this.vaultAccess = vaultAccess;
        this.playerDifficultyRepository = playerDifficultyRepository;
    }

    @EventHandler(ignoreCancelled = true)
    public void onVaultInteract(PlayerInteractEvent event) {
        if (!isRelevantInteract(event)) {
            return;
        }

        if (!manager.isValidLootAttempt(event.getClickedBlock(), event.getItem())) {
            return;
        }

        manager.unlockIfLegacyOrExpired(event.getClickedBlock(), event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(ignoreCancelled = true)
    public void onVaultLootDispensed(BlockDispenseLootEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        if (event.getBlock().getType() != Material.VAULT) {
            return;
        }

        manager.recordSuccessfulLoot(event.getBlock(), event.getPlayer().getUniqueId(), System.currentTimeMillis());
        awardDifficultyProgression(event.getBlock(), event.getPlayer().getUniqueId());
    }

    private void awardDifficultyProgression(Block block, UUID playerId) {
        if (!plugin.isStrengthenTrialMobs()) {
            return;
        }

        int pointsToAward = switch (vaultAccess.classifyVault(block)) {
            case NORMAL -> plugin.getDifficultySettings().progression().normalVaultPoints();
            case OMINOUS -> plugin.getDifficultySettings().progression().ominousVaultPoints();
            case OTHER -> 0;
        };

        if (pointsToAward > 0) {
            playerDifficultyRepository.addPoints(playerId, pointsToAward);
        }
    }

    private boolean isRelevantInteract(PlayerInteractEvent event) {
        return event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getHand() == EquipmentSlot.HAND
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.VAULT;
    }
}
