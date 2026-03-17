package de.t14d3.trickiertrials;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;

public final class TrialVaultResetManager {

    private final TrialVaultRepository repository;
    private final VaultAccess vaultAccess;

    private long trialVaultResetTime;
    private Set<Material> allowedKeyMaterials;

    public TrialVaultResetManager(
            TrialVaultRepository repository,
            VaultAccess vaultAccess,
            long trialVaultResetTime,
            Set<Material> allowedKeyMaterials
    ) {
        this.repository = repository;
        this.vaultAccess = vaultAccess;
        reloadSettings(trialVaultResetTime, allowedKeyMaterials);
    }

    public void reloadSettings(long trialVaultResetTime, Set<Material> allowedKeyMaterials) {
        this.trialVaultResetTime = trialVaultResetTime;
        this.allowedKeyMaterials = Set.copyOf(allowedKeyMaterials);
    }

    public boolean isManagedVault(Block block) {
        return vaultAccess.isManagedVault(block, allowedKeyMaterials);
    }

    public boolean isValidLootAttempt(Block block, ItemStack heldItem) {
        return vaultAccess.isValidLootAttempt(block, heldItem, allowedKeyMaterials);
    }

    public void recordSuccessfulLoot(Block block, UUID playerId, long now) {
        if (trialVaultResetTime == -1L || !isManagedVault(block)) {
            return;
        }

        repository.putPlayerTimestamp(toLocation(block), playerId, now);
    }

    public boolean unlockIfLegacyOrExpired(Block block, UUID playerId, long now) {
        if (trialVaultResetTime == -1L || !isManagedVault(block)) {
            return false;
        }

        TrialVaultRepository.VaultLocation location = toLocation(block);
        Long lastUse = repository.getPlayerTimestamp(location, playerId);
        boolean rewarded = vaultAccess.hasRewardedPlayer(block, playerId);

        if (!rewarded) {
            if (lastUse != null) {
                repository.removePlayerTimestamp(location, playerId);
            }
            return false;
        }

        if (lastUse == null || isExpired(lastUse, now)) {
            boolean removed = vaultAccess.removeRewardedPlayer(block, playerId);
            if (lastUse != null) {
                repository.removePlayerTimestamp(location, playerId);
            }
            return removed;
        }

        return false;
    }

    public void runResetPass() {
        if (trialVaultResetTime == -1L) {
            return;
        }

        long now = System.currentTimeMillis();
        for (TrialVaultRepository.TrackedVault trackedVault : repository.snapshotTrackedVaults()) {
            processTrackedVault(trackedVault, now);
        }
    }

    private void processTrackedVault(TrialVaultRepository.TrackedVault trackedVault, long now) {
        TrialVaultRepository.VaultLocation location = trackedVault.location();
        World world = Bukkit.getWorld(location.worldUuid());
        if (world == null) {
            repository.forgetVault(location);
            return;
        }

        if (!world.isChunkLoaded(location.chunkX(), location.chunkZ())) {
            return;
        }

        Block block = world.getBlockAt(location.x(), location.y(), location.z());
        if (block.getType() != Material.VAULT) {
            repository.forgetVault(location);
            return;
        }

        if (!isManagedVault(block)) {
            repository.forgetVault(location);
            return;
        }

        for (var entry : trackedVault.playerResetTimes().entrySet()) {
            UUID playerId = entry.getKey();
            long lastUse = entry.getValue();

            if (!vaultAccess.hasRewardedPlayer(block, playerId)) {
                repository.removePlayerTimestamp(location, playerId);
                continue;
            }

            if (isExpired(lastUse, now) && vaultAccess.removeRewardedPlayer(block, playerId)) {
                repository.removePlayerTimestamp(location, playerId);
            }
        }
    }

    private boolean isExpired(long lastUse, long now) {
        return now >= lastUse + trialVaultResetTime;
    }

    private TrialVaultRepository.VaultLocation toLocation(Block block) {
        return TrialVaultRepository.VaultLocation.fromBlock(block);
    }
}
