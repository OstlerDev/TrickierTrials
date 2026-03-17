package de.t14d3.trickiertrials;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class TrialVaultRefresher implements Listener {

    private final TrialVaultResetManager manager;

    public TrialVaultRefresher(TrialVaultResetManager manager) {
        this.manager = manager;
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
    }

    private boolean isRelevantInteract(PlayerInteractEvent event) {
        return event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getHand() == EquipmentSlot.HAND
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.VAULT;
    }
}
