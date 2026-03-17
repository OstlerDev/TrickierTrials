package de.t14d3.trickiertrials;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TrickierTrials extends JavaPlugin implements CommandExecutor {

    private static final long VAULT_RESET_TASK_PERIOD_TICKS = 20L * 60L;

    private List<Material> trialChamberMaterials;
    private Set<Material> vaultResetAllowedKeyMaterials;

    private boolean decayPlacedBlocks;
    private boolean regenerateBrokenBlocks;
    private boolean strengthenTrialMobs;
    private boolean glowingEffect;
    private boolean secret;
    private String secretName;

    private long trialVaultResetTime;
    private TrialVaultResetManager trialVaultResetManager;
    private BukkitTask trialVaultResetTask;
    private VaultAccess vaultAccess;

    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig(); // Creates the config file with default values if it doesn't exist
        loadTrialChamberMaterials(); // Load trial chamber materials from config
        loadConfigurationOptions(); // Load decay and regeneration settings
        loadVaultResetAllowedKeyMaterials();

        TrialVaultRepository trialVaultRepository = new TrialVaultRepository(this);
        trialVaultRepository.loadAll();
        vaultAccess = new VaultAccess(getLogger());
        trialVaultResetManager = new TrialVaultResetManager(trialVaultRepository, vaultAccess, trialVaultResetTime, vaultResetAllowedKeyMaterials);

        // Register event listeners
        this.getServer().getPluginManager().registerEvents(new TrialSpawnerListener(this, strengthenTrialMobs, glowingEffect, secret, secretName), this);
        this.getServer().getPluginManager().registerEvents(new TrialChamberProtector(this, getTrialChamberMaterials(), decayPlacedBlocks, regenerateBrokenBlocks), this);
        this.getServer().getPluginManager().registerEvents(new TrialDeathListener(), this);
        this.getServer().getPluginManager().registerEvents(new TrialVaultRefresher(trialVaultResetManager), this);

        startTrialVaultResetTask();

        // Register command executor
        this.getCommand("trickiertrials").setExecutor(this);
    }

    @Override
    public void onDisable() {
        stopTrialVaultResetTask();
    }

    private void loadTrialChamberMaterials() {
        FileConfiguration config = getConfig();
        List<String> blockNames = config.getStringList("blocks-to-protect");
        trialChamberMaterials = new ArrayList<>();

        for (String name : blockNames) {
            try {
                Material material = Material.valueOf(name);
                trialChamberMaterials.add(material);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid material in config: " + name);
            }
        }
    }

    private void loadConfigurationOptions() {
        FileConfiguration config = getConfig();
        decayPlacedBlocks = config.getBoolean("decay-placed-blocks", true);
        regenerateBrokenBlocks = config.getBoolean("regenerate-broken-blocks", true);
        strengthenTrialMobs = config.getBoolean("strengthen-trial-mobs", true);
        trialVaultResetTime = config.getLong("trial-vault-reset-time", 86400000L);
        glowingEffect = config.getBoolean("glowing-effect", true);
        secret = config.getBoolean("easter-egg", false);
        secretName = config.getString("easter-egg-name", "Klein Tiade");

        // Config migrator
        if (config.get("decay-delay") == null) {
            config.set("decay-delay", "10 #Decay delay in seconds");
        }
        if (config.get("regenerate-delay") == null) {
            config.set("regenerate-delay", "10 #Regeneration delay in seconds, plus a random delay of up to 100 ticks");
        }
        if (config.get("mining-fatigue-level") == null) {
            config.set("mining-fatigue-level", 2);
        }
    }

    private void loadVaultResetAllowedKeyMaterials() {
        FileConfiguration config = getConfig();
        List<String> materialNames = config.getStringList("vault-reset-allowed-key-materials");
        if (materialNames.isEmpty() && !config.isList("vault-reset-allowed-key-materials")) {
            materialNames = List.of("TRIAL_KEY", "OMINOUS_TRIAL_KEY");
        }

        vaultResetAllowedKeyMaterials = new HashSet<>();
        for (String name : materialNames) {
            try {
                vaultResetAllowedKeyMaterials.add(Material.valueOf(name));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid vault key material in config: " + name);
            }
        }
    }

    private void startTrialVaultResetTask() {
        stopTrialVaultResetTask();
        if (trialVaultResetTime == -1L || trialVaultResetManager == null) {
            return;
        }

        trialVaultResetTask = getServer().getScheduler().runTaskTimer(
                this,
                trialVaultResetManager::runResetPass,
                VAULT_RESET_TASK_PERIOD_TICKS,
                VAULT_RESET_TASK_PERIOD_TICKS
        );
    }

    private void stopTrialVaultResetTask() {
        if (trialVaultResetTask != null) {
            trialVaultResetTask.cancel();
            trialVaultResetTask = null;
        }
    }

    private void reloadVaultResetComponents() {
        if (trialVaultResetManager == null) {
            return;
        }

        trialVaultResetManager.reloadSettings(trialVaultResetTime, vaultResetAllowedKeyMaterials);
        startTrialVaultResetTask();
    }

    public List<Material> getTrialChamberMaterials() {
        return trialChamberMaterials;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("trickiertrials")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                // Reload the configuration
                reloadConfig();
                loadTrialChamberMaterials(); // Reload trial chamber materials
                loadConfigurationOptions(); // Reload other options
                loadVaultResetAllowedKeyMaterials();
                reloadVaultResetComponents();
                sender.sendMessage("Trickier Trials configuration reloaded successfully.");
                return true;
            } else {
                // Handle other command logic here (if applicable)
                sender.sendMessage("Usage: /trickiertrials reload");
                return true;
            }
        }
        return false;
    }
}
