package de.t14d3.trickiertrials;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerDifficultyRepository {

    private final JavaPlugin plugin;
    private final File storageFile;
    private final Map<UUID, Integer> playerPoints;

    public PlayerDifficultyRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "player-difficulty.yml");
        this.playerPoints = new HashMap<>();
    }

    public void loadAll() {
        playerPoints.clear();
        if (!storageFile.exists()) {
            return;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection playerSection = configuration.getConfigurationSection("players");
        if (playerSection == null) {
            return;
        }

        for (String key : playerSection.getKeys(false)) {
            try {
                int points = Math.max(0, playerSection.getInt(key));
                if (points > 0) {
                    playerPoints.put(UUID.fromString(key), points);
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid player UUID in player difficulty file: " + key);
            }
        }
    }

    public int getPoints(UUID playerId) {
        return playerPoints.getOrDefault(playerId, 0);
    }

    public int addPoints(UUID playerId, int amount) {
        if (amount <= 0) {
            return getPoints(playerId);
        }

        int updatedPoints = getPoints(playerId) + amount;
        playerPoints.put(playerId, updatedPoints);
        save();
        return updatedPoints;
    }

    private void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for player difficulty storage.");
            return;
        }

        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : playerPoints.entrySet()) {
            configuration.set("players." + entry.getKey(), entry.getValue());
        }

        try {
            configuration.save(storageFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save player difficulty file: " + storageFile.getName());
            exception.printStackTrace();
        }
    }
}
