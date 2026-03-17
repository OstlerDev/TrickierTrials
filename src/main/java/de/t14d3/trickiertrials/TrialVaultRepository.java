package de.t14d3.trickiertrials;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TrialVaultRepository {

    public record VaultLocation(UUID worldUuid, String worldName, int x, int y, int z) {

        public static VaultLocation fromBlock(Block block) {
            return new VaultLocation(
                    block.getWorld().getUID(),
                    block.getWorld().getName(),
                    block.getX(),
                    block.getY(),
                    block.getZ()
            );
        }

        public int chunkX() {
            return x >> 4;
        }

        public int chunkZ() {
            return z >> 4;
        }

        public String fileName() {
            return worldUuid + "_" + x + "_" + y + "_" + z + ".yml";
        }
    }

    public record TrackedVault(VaultLocation location, Map<UUID, Long> playerResetTimes) {
    }

    private final JavaPlugin plugin;
    private final File vaultDirectory;
    private final Map<VaultLocation, Map<UUID, Long>> trackedVaults;

    public TrialVaultRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vaultDirectory = new File(plugin.getDataFolder(), "vaults");
        this.trackedVaults = new HashMap<>();
    }

    public void loadAll() {
        trackedVaults.clear();
        if (!vaultDirectory.exists() && !vaultDirectory.mkdirs()) {
            plugin.getLogger().warning("Could not create vault tracking directory.");
            return;
        }

        File[] files = vaultDirectory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            TrackedVault trackedVault = loadVault(file);
            if (trackedVault == null || trackedVault.playerResetTimes().isEmpty()) {
                if (file.exists() && !file.delete()) {
                    plugin.getLogger().warning("Could not delete empty or invalid vault file: " + file.getName());
                }
                continue;
            }
            trackedVaults.put(trackedVault.location(), new HashMap<>(trackedVault.playerResetTimes()));
        }
    }

    public List<TrackedVault> snapshotTrackedVaults() {
        List<TrackedVault> snapshot = new ArrayList<>();
        for (Map.Entry<VaultLocation, Map<UUID, Long>> entry : trackedVaults.entrySet()) {
            snapshot.add(new TrackedVault(entry.getKey(), Map.copyOf(entry.getValue())));
        }
        return snapshot;
    }

    public Long getPlayerTimestamp(VaultLocation location, UUID playerId) {
        Map<UUID, Long> players = trackedVaults.get(location);
        return players == null ? null : players.get(playerId);
    }

    public void putPlayerTimestamp(VaultLocation location, UUID playerId, long timestamp) {
        trackedVaults.computeIfAbsent(location, ignored -> new HashMap<>()).put(playerId, timestamp);
        saveVault(location);
    }

    public void removePlayerTimestamp(VaultLocation location, UUID playerId) {
        Map<UUID, Long> players = trackedVaults.get(location);
        if (players == null) {
            return;
        }

        players.remove(playerId);
        if (players.isEmpty()) {
            deleteVault(location);
            return;
        }

        saveVault(location);
    }

    public void forgetVault(VaultLocation location) {
        deleteVault(location);
    }

    private void saveVault(VaultLocation location) {
        Map<UUID, Long> players = trackedVaults.get(location);
        if (players == null || players.isEmpty()) {
            deleteVault(location);
            return;
        }

        if (!vaultDirectory.exists() && !vaultDirectory.mkdirs()) {
            plugin.getLogger().warning("Could not create vault tracking directory.");
            return;
        }

        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("world-uuid", location.worldUuid().toString());
        configuration.set("world-name", location.worldName());
        configuration.set("x", location.x());
        configuration.set("y", location.y());
        configuration.set("z", location.z());

        for (Map.Entry<UUID, Long> entry : players.entrySet()) {
            configuration.set("players." + entry.getKey(), entry.getValue());
        }

        try {
            configuration.save(getVaultFile(location));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save vault file: " + getVaultFile(location).getName());
            exception.printStackTrace();
        }
    }

    private void deleteVault(VaultLocation location) {
        trackedVaults.remove(location);
        File file = getVaultFile(location);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete vault file: " + file.getName());
        }
    }

    private File getVaultFile(VaultLocation location) {
        return new File(vaultDirectory, location.fileName());
    }

    private TrackedVault loadVault(File file) {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);

        String worldUuidString = configuration.getString("world-uuid");
        String worldName = configuration.getString("world-name", "");
        if (worldUuidString == null) {
            plugin.getLogger().warning("Invalid vault file missing world UUID: " + file.getName());
            return null;
        }

        UUID worldUuid;
        try {
            worldUuid = UUID.fromString(worldUuidString);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid world UUID in vault file: " + file.getName());
            return null;
        }

        VaultLocation location = new VaultLocation(
                worldUuid,
                worldName,
                configuration.getInt("x"),
                configuration.getInt("y"),
                configuration.getInt("z")
        );

        Map<UUID, Long> players = new HashMap<>();
        ConfigurationSection playerSection = configuration.getConfigurationSection("players");
        if (playerSection != null) {
            for (String key : playerSection.getKeys(false)) {
                try {
                    players.put(UUID.fromString(key), playerSection.getLong(key));
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Invalid player UUID in vault file: " + file.getName());
                }
            }
        }

        return new TrackedVault(location, players);
    }
}
