package de.t14d3.trickiertrials;

import com.tchristofferson.configupdater.ConfigUpdater;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

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
    private DifficultySettings difficultySettings;
    private PlayerDifficultyRepository playerDifficultyRepository;
    private final TrialDifficultyCalculator trialDifficultyCalculator = new TrialDifficultyCalculator();
    private final TrialMobGearGenerator trialMobGearGenerator = new TrialMobGearGenerator();

    private record RuntimeConfiguration(
            List<Material> trialChamberMaterials,
            Set<Material> vaultResetAllowedKeyMaterials,
            boolean decayPlacedBlocks,
            boolean regenerateBrokenBlocks,
            boolean strengthenTrialMobs,
            boolean glowingEffect,
            boolean secret,
            String secretName,
            long trialVaultResetTime,
            DifficultySettings difficultySettings
    ) {
    }

    @Override
    public void onEnable() {
        try {
            applyRuntimeConfiguration(loadRuntimeConfiguration());
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to load plugin configuration.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        TrialVaultRepository trialVaultRepository = new TrialVaultRepository(this);
        trialVaultRepository.loadAll();
        playerDifficultyRepository = new PlayerDifficultyRepository(this);
        playerDifficultyRepository.loadAll();
        vaultAccess = new VaultAccess(getLogger());
        trialVaultResetManager = new TrialVaultResetManager(trialVaultRepository, vaultAccess, trialVaultResetTime, vaultResetAllowedKeyMaterials);

        // Register event listeners
        this.getServer().getPluginManager().registerEvents(new TrialSpawnerListener(this, trialDifficultyCalculator, playerDifficultyRepository, trialMobGearGenerator), this);
        this.getServer().getPluginManager().registerEvents(new TrialChamberProtector(this, getTrialChamberMaterials(), decayPlacedBlocks, regenerateBrokenBlocks), this);
        this.getServer().getPluginManager().registerEvents(new TrialDeathListener(), this);
        this.getServer().getPluginManager().registerEvents(new TrialVaultRefresher(this, trialVaultResetManager, vaultAccess, playerDifficultyRepository), this);

        startTrialVaultResetTask();

        // Register command executor
        this.getCommand("trickiertrials").setExecutor(this);
    }

    @Override
    public void onDisable() {
        stopTrialVaultResetTask();
    }

    private RuntimeConfiguration loadRuntimeConfiguration() throws IOException {
        FileConfiguration config = YamlConfiguration.loadConfiguration(updateBundledConfig("config.yml"));
        return new RuntimeConfiguration(
                List.copyOf(loadTrialChamberMaterials(config)),
                Set.copyOf(loadVaultResetAllowedKeyMaterials(config)),
                config.getBoolean("decay-placed-blocks", true),
                config.getBoolean("regenerate-broken-blocks", true),
                config.getBoolean("strengthen-trial-mobs", true),
                config.getBoolean("glowing-effect", true),
                config.getBoolean("easter-egg", false),
                config.getString("easter-egg-name", "Klein Tiade"),
                config.getLong("trial-vault-reset-time", 86400000L),
                loadDifficultyConfiguration(updateBundledConfig("difficulty.yml"))
        );
    }

    private File updateBundledConfig(String resourceName) throws IOException {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IOException("Could not create plugin data folder.");
        }

        File configFile = new File(getDataFolder(), resourceName);
        if (!configFile.exists()) {
            if ("config.yml".equals(resourceName)) {
                saveDefaultConfig();
            } else {
                saveResource(resourceName, false);
            }
        }

        if (!configFile.exists()) {
            throw new IOException("Could not create " + resourceName + ".");
        }

        ConfigUpdater.update(this, resourceName, configFile);
        return configFile;
    }

    private void applyRuntimeConfiguration(RuntimeConfiguration configuration) {
        reloadConfig();
        trialChamberMaterials = configuration.trialChamberMaterials();
        vaultResetAllowedKeyMaterials = configuration.vaultResetAllowedKeyMaterials();
        decayPlacedBlocks = configuration.decayPlacedBlocks();
        regenerateBrokenBlocks = configuration.regenerateBrokenBlocks();
        strengthenTrialMobs = configuration.strengthenTrialMobs();
        glowingEffect = configuration.glowingEffect();
        secret = configuration.secret();
        secretName = configuration.secretName();
        trialVaultResetTime = configuration.trialVaultResetTime();
        difficultySettings = configuration.difficultySettings();
    }

    private List<Material> loadTrialChamberMaterials(FileConfiguration config) {
        List<String> blockNames = config.getStringList("blocks-to-protect");
        List<Material> materials = new ArrayList<>();

        for (String name : blockNames) {
            try {
                Material material = Material.valueOf(name);
                materials.add(material);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid material in config: " + name);
            }
        }

        return materials;
    }

    private Set<Material> loadVaultResetAllowedKeyMaterials(FileConfiguration config) {
        List<String> materialNames = config.getStringList("vault-reset-allowed-key-materials");
        if (materialNames.isEmpty() && !config.isList("vault-reset-allowed-key-materials")) {
            materialNames = List.of("TRIAL_KEY", "OMINOUS_TRIAL_KEY");
        }

        Set<Material> allowedMaterials = new HashSet<>();
        for (String name : materialNames) {
            try {
                allowedMaterials.add(Material.valueOf(name));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid vault key material in config: " + name);
            }
        }

        return allowedMaterials;
    }

    private DifficultySettings loadDifficultyConfiguration(File difficultyConfigFile) {
        try {
            return DifficultySettings.load(YamlConfiguration.loadConfiguration(difficultyConfigFile));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid difficulty.yml: " + exception.getMessage(), exception);
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

    public boolean isStrengthenTrialMobs() {
        return strengthenTrialMobs;
    }

    public boolean isGlowingEffect() {
        return glowingEffect;
    }

    public boolean isSecret() {
        return secret;
    }

    public String getSecretName() {
        return secretName;
    }

    public DifficultySettings getDifficultySettings() {
        return difficultySettings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("trickiertrials")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                try {
                    applyRuntimeConfiguration(loadRuntimeConfiguration());
                    reloadVaultResetComponents();
                    sender.sendMessage("Trickier Trials configuration reloaded successfully.");
                } catch (Exception exception) {
                    getLogger().log(Level.SEVERE, "Failed to reload plugin configuration.", exception);
                    sender.sendMessage("Failed to reload Trickier Trials configuration. Check the server log for details.");
                }
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
