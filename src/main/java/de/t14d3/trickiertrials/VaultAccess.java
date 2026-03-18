package de.t14d3.trickiertrials;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Vault;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class VaultAccess {

    private final boolean supportsModernVaultApi;
    private Field rewardedPlayersField;

    public VaultAccess(Logger logger) {
        this.supportsModernVaultApi = detectModernVaultApi();
        logger.info(supportsModernVaultApi
                ? "Using modern Paper vault access."
                : "Using legacy compatibility vault access.");
    }

    public boolean isManagedVault(Block block, Set<Material> allowedKeyMaterials) {
        if (supportsModernVaultApi) {
            if (!(block.getState() instanceof Vault vault)) {
                return false;
            }
            return allowedKeyMaterials.contains(vault.getKeyItem().getType());
        }

        return legacyIsManagedVault(block, allowedKeyMaterials);
    }

    public boolean isValidLootAttempt(Block block, ItemStack heldItem, Set<Material> allowedKeyMaterials) {
        if (heldItem == null) {
            return false;
        }

        if (supportsModernVaultApi) {
            if (!(block.getState() instanceof Vault vault)) {
                return false;
            }

            ItemStack requiredKey = vault.getKeyItem();
            return allowedKeyMaterials.contains(requiredKey.getType())
                    && heldItem.isSimilar(requiredKey)
                    && heldItem.getAmount() >= requiredKey.getAmount();
        }

        return legacyIsValidLootAttempt(block, heldItem, allowedKeyMaterials);
    }

    public boolean hasRewardedPlayer(Block block, UUID playerId) {
        if (supportsModernVaultApi) {
            return block.getState() instanceof Vault vault && vault.hasRewardedPlayer(playerId);
        }

        return legacyHasRewardedPlayer(block, playerId);
    }

    public VaultKeyType classifyVault(Block block) {
        Material keyMaterial = getRequiredKeyMaterial(block);
        if (keyMaterial == Material.TRIAL_KEY) {
            return VaultKeyType.NORMAL;
        }
        if (keyMaterial == Material.OMINOUS_TRIAL_KEY) {
            return VaultKeyType.OMINOUS;
        }
        return VaultKeyType.OTHER;
    }

    public boolean removeRewardedPlayer(Block block, UUID playerId) {
        if (supportsModernVaultApi) {
            if (!(block.getState() instanceof Vault vault)) {
                return false;
            }

            boolean removed = vault.removeRewardedPlayer(playerId);
            if (removed) {
                vault.update();
            }
            return removed;
        }

        return legacyRemoveRewardedPlayer(block, playerId);
    }

    private boolean detectModernVaultApi() {
        try {
            Vault.class.getMethod("getKeyItem");
            Vault.class.getMethod("hasRewardedPlayer", UUID.class);
            Vault.class.getMethod("removeRewardedPlayer", UUID.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    private boolean legacyIsManagedVault(Block block, Set<Material> allowedKeyMaterials) {
        VaultBlockEntity vaultBlockEntity = getVaultBlockEntity(block);
        return vaultBlockEntity != null && allowedKeyMaterials.contains(getRequiredKey(vaultBlockEntity).getType());
    }

    private boolean legacyIsValidLootAttempt(Block block, ItemStack heldItem, Set<Material> allowedKeyMaterials) {
        VaultBlockEntity vaultBlockEntity = getVaultBlockEntity(block);
        if (vaultBlockEntity == null) {
            return false;
        }

        ItemStack requiredKey = getRequiredKey(vaultBlockEntity);
        return allowedKeyMaterials.contains(requiredKey.getType())
                && heldItem.isSimilar(requiredKey)
                && heldItem.getAmount() >= requiredKey.getAmount();
    }

    private boolean legacyHasRewardedPlayer(Block block, UUID playerId) {
        VaultBlockEntity vaultBlockEntity = getVaultBlockEntity(block);
        return vaultBlockEntity != null && getRewardedPlayers(vaultBlockEntity.getServerData()).contains(playerId);
    }

    private boolean legacyRemoveRewardedPlayer(Block block, UUID playerId) {
        VaultBlockEntity vaultBlockEntity = getVaultBlockEntity(block);
        if (vaultBlockEntity == null) {
            return false;
        }

        boolean removed = getRewardedPlayers(vaultBlockEntity.getServerData()).remove(playerId);
        if (removed) {
            markVaultChanged(block, vaultBlockEntity);
        }
        return removed;
    }

    private Material getRequiredKeyMaterial(Block block) {
        if (supportsModernVaultApi) {
            if (!(block.getState() instanceof Vault vault)) {
                return null;
            }

            ItemStack requiredKey = vault.getKeyItem();
            return requiredKey == null ? null : requiredKey.getType();
        }

        VaultBlockEntity vaultBlockEntity = getVaultBlockEntity(block);
        return vaultBlockEntity == null ? null : getRequiredKey(vaultBlockEntity).getType();
    }

    private ItemStack getRequiredKey(VaultBlockEntity vaultBlockEntity) {
        return CraftItemStack.asBukkitCopy(vaultBlockEntity.getConfig().keyItem());
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> getRewardedPlayers(VaultServerData vaultServerData) {
        try {
            if (rewardedPlayersField == null) {
                rewardedPlayersField = resolveRewardedPlayersField();
            }
            return (Set<UUID>) rewardedPlayersField.get(vaultServerData);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access vault rewarded players field.", exception);
        }
    }

    private Field resolveRewardedPlayersField() throws NoSuchFieldException {
        for (String candidate : List.of("rewardedPlayers", "f_315329_", "field_48888", "e")) {
            try {
                Field field = VaultServerData.class.getDeclaredField(candidate);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }

        for (Field field : VaultServerData.class.getDeclaredFields()) {
            if (Set.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }

        throw new NoSuchFieldException("rewardedPlayers");
    }

    private VaultBlockEntity getVaultBlockEntity(Block block) {
        ServerLevel serverLevel = ((CraftWorld) block.getWorld()).getHandle();
        BlockEntity blockEntity = serverLevel.getBlockEntity(new BlockPos(block.getX(), block.getY(), block.getZ()));
        return blockEntity instanceof VaultBlockEntity vaultBlockEntity ? vaultBlockEntity : null;
    }

    private void markVaultChanged(Block block, VaultBlockEntity vaultBlockEntity) {
        vaultBlockEntity.setChanged();

        ServerLevel serverLevel = ((CraftWorld) block.getWorld()).getHandle();
        BlockPos blockPos = new BlockPos(block.getX(), block.getY(), block.getZ());
        net.minecraft.world.level.block.state.BlockState blockState = serverLevel.getBlockState(blockPos);
        serverLevel.sendBlockUpdated(blockPos, blockState, blockState, 3);
    }
}
