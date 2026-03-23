package de.t14d3.trickiertrials;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record DifficultySettings(
        double scale,
        ProgressionSettings progression,
        CombatSettings combat,
        ReadinessSettings readiness,
        MobGearSettings mobGear
) {

    public static DifficultySettings load(FileConfiguration config) {
        ConfigurationSection difficulty = requiredSection(config, "difficulty");
        return new DifficultySettings(
                requiredBoundedDouble(difficulty, "scale", 0D, 1D),
                loadProgression(requiredSection(difficulty, "progression")),
                loadCombat(requiredSection(difficulty, "combat")),
                loadReadiness(requiredSection(difficulty, "readiness")),
                loadMobGear(requiredSection(difficulty, "mob-gear"))
        );
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ProgressionSettings loadProgression(ConfigurationSection section) {
        return new ProgressionSettings(
                requiredPositiveInt(section, "normal-vault-points"),
                requiredPositiveInt(section, "ominous-vault-points"),
                requiredPositiveDouble(section, "points-per-level"),
                requiredNonNegativeDouble(section, "max-level"),
                requiredNonNegativeDouble(section, "score-bonus-per-level")
        );
    }

    private static CombatSettings loadCombat(ConfigurationSection section) {
        return new CombatSettings(
                requiredPositiveDouble(section, "score-at-max-difficulty"),
                requiredAtLeast(section, "max-health-multiplier", 1D),
                requiredAtLeast(section, "max-damage-multiplier", 1D)
        );
    }

    private static ReadinessSettings loadReadiness(ConfigurationSection section) {
        return new ReadinessSettings(
                Map.copyOf(loadArmorScores(requiredSection(section, "armor-scores"))),
                Map.copyOf(loadMaterialScores(requiredSection(section, "weapon-scores")))
        );
    }

    private static Map<String, Double> loadArmorScores(ConfigurationSection section) {
        Map<String, Double> armorScores = new HashMap<>();
        for (String key : section.getKeys(false)) {
            String normalizedKey = key.toUpperCase(Locale.ROOT);
            if (!isSupportedArmorKey(normalizedKey)) {
                throw invalidValue(section, key, "unsupported armor family");
            }
            armorScores.put(normalizedKey, requiredNonNegativeDouble(section, key));
        }
        return armorScores;
    }

    private static Map<Material, Double> loadMaterialScores(ConfigurationSection section) {
        Map<Material, Double> scores = new HashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                scores.put(Material.valueOf(key.toUpperCase(Locale.ROOT)), requiredNonNegativeDouble(section, key));
            } catch (IllegalArgumentException exception) {
                throw invalidValue(section, key, "invalid material: " + key);
            }
        }
        return scores;
    }

    private static MobGearSettings loadMobGear(ConfigurationSection section) {
        ConfigurationSection armorSection = requiredSection(section, "armor");
        ConfigurationSection weaponSection = requiredSection(section, "weapons");

        Map<ArmorSlot, ChanceRange> slotChances = new EnumMap<>(ArmorSlot.class);
        ConfigurationSection slotSection = requiredSection(armorSection, "slot-chances");
        for (ArmorSlot slot : ArmorSlot.values()) {
            slotChances.put(slot, loadChanceRange(requiredSection(slotSection, slot.configKey())));
        }

        return new MobGearSettings(
                requiredBoundedDouble(section, "quality-jitter", 0D, 1D),
                loadChanceRange(requiredSection(armorSection, "overall-chance")),
                Map.copyOf(slotChances),
                List.copyOf(loadArmorTiers(requiredSection(armorSection, "tiers"))),
                loadChanceRange(requiredSection(weaponSection, "overall-chance")),
                List.copyOf(loadWeaponChoices(requiredSection(weaponSection, "choices")))
        );
    }

    private static List<ArmorTier> loadArmorTiers(ConfigurationSection section) {
        List<ArmorTier> tiers = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection tier = requiredSection(section, key);
            ConfigurationSection piecesSection = requiredSection(tier, "pieces");
            Map<ArmorSlot, Material> pieces = new EnumMap<>(ArmorSlot.class);
            for (ArmorSlot slot : ArmorSlot.values()) {
                pieces.put(slot, requiredMaterial(piecesSection, slot.configKey()));
            }
            tiers.add(new ArmorTier(
                    requiredBoundedDouble(tier, "min-difficulty", 0D, 1D),
                    requiredPositiveDouble(tier, "weight"),
                    Map.copyOf(pieces)
            ));
        }
        if (tiers.isEmpty()) {
            throw invalidValue(section, "<root>", "must define at least one armor tier");
        }
        return tiers;
    }

    private static List<WeaponChoice> loadWeaponChoices(ConfigurationSection section) {
        List<WeaponChoice> choices = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection choice = requiredSection(section, key);
            choices.add(new WeaponChoice(
                    requiredBoundedDouble(choice, "min-difficulty", 0D, 1D),
                    requiredPositiveDouble(choice, "weight"),
                    requiredMaterial(choice, "material")
            ));
        }
        if (choices.isEmpty()) {
            throw invalidValue(section, "<root>", "must define at least one weapon choice");
        }
        return choices;
    }

    private static ChanceRange loadChanceRange(ConfigurationSection section) {
        double min = requiredBoundedDouble(section, "min", 0D, 1D);
        double max = requiredBoundedDouble(section, "max", 0D, 1D);
        if (min > max) {
            throw invalidValue(section, "min/max", "min must be less than or equal to max");
        }
        return new ChanceRange(min, max);
    }

    private static ConfigurationSection requiredSection(ConfigurationSection parent, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw missingValue(parent, key);
        }
        return section;
    }

    private static int requiredPositiveInt(ConfigurationSection section, String key) {
        int value = requiredInt(section, key);
        if (value <= 0) {
            throw invalidValue(section, key, "must be > 0");
        }
        return value;
    }

    private static int requiredInt(ConfigurationSection section, String key) {
        if (!section.contains(key)) {
            throw missingValue(section, key);
        }
        return section.getInt(key);
    }

    private static double requiredPositiveDouble(ConfigurationSection section, String key) {
        double value = requiredDouble(section, key);
        if (value <= 0D) {
            throw invalidValue(section, key, "must be > 0");
        }
        return value;
    }

    private static double requiredNonNegativeDouble(ConfigurationSection section, String key) {
        double value = requiredDouble(section, key);
        if (value < 0D) {
            throw invalidValue(section, key, "must be >= 0");
        }
        return value;
    }

    private static double requiredAtLeast(ConfigurationSection section, String key, double minimum) {
        double value = requiredDouble(section, key);
        if (value < minimum) {
            throw invalidValue(section, key, "must be >= " + minimum);
        }
        return value;
    }

    private static double requiredBoundedDouble(ConfigurationSection section, String key, double min, double max) {
        double value = requiredDouble(section, key);
        if (value < min || value > max) {
            throw invalidValue(section, key, "must be between " + min + " and " + max);
        }
        return value;
    }

    private static double requiredDouble(ConfigurationSection section, String key) {
        if (!section.contains(key)) {
            throw missingValue(section, key);
        }
        return section.getDouble(key);
    }

    private static Material requiredMaterial(ConfigurationSection section, String key) {
        if (!section.contains(key)) {
            throw missingValue(section, key);
        }

        String materialName = section.getString(key);
        if (materialName == null || materialName.isBlank()) {
            throw invalidValue(section, key, "must not be blank");
        }

        try {
            return Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidValue(section, key, "invalid material: " + materialName);
        }
    }

    private static boolean isSupportedArmorKey(String key) {
        return switch (key) {
            case "LEATHER", "GOLDEN", "CHAINMAIL", "IRON", "DIAMOND", "NETHERITE", "ELYTRA" -> true;
            default -> false;
        };
    }

    private static IllegalArgumentException missingValue(ConfigurationSection section, String key) {
        return new IllegalArgumentException("Missing configuration value: " + path(section, key));
    }

    private static IllegalArgumentException invalidValue(ConfigurationSection section, String key, String message) {
        return new IllegalArgumentException("Invalid configuration value at " + path(section, key) + ": " + message);
    }

    private static String path(ConfigurationSection section, String key) {
        String currentPath = section.getCurrentPath();
        return currentPath == null || currentPath.isBlank() ? key : currentPath + "." + key;
    }

    public record ProgressionSettings(
            int normalVaultPoints,
            int ominousVaultPoints,
            double pointsPerLevel,
            double maxLevel,
            double scoreBonusPerLevel
    ) {
    }

    public record CombatSettings(
            double scoreAtMaxDifficulty,
            double maxHealthMultiplier,
            double maxDamageMultiplier
    ) {
    }

    public enum ArmorSlot {
        HELMET("helmet"),
        CHESTPLATE("chestplate"),
        LEGGINGS("leggings"),
        BOOTS("boots");

        private final String configKey;

        ArmorSlot(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }
    }

    public record ReadinessSettings(
            Map<String, Double> armorScores,
            Map<Material, Double> weaponScores
    ) {
        public double armorScore(Material material) {
            String key = switch (material) {
                case ELYTRA -> "ELYTRA";
                case LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS -> "LEATHER";
                case GOLDEN_HELMET, GOLDEN_CHESTPLATE, GOLDEN_LEGGINGS, GOLDEN_BOOTS -> "GOLDEN";
                case CHAINMAIL_HELMET, CHAINMAIL_CHESTPLATE, CHAINMAIL_LEGGINGS, CHAINMAIL_BOOTS -> "CHAINMAIL";
                case IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS -> "IRON";
                case DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS -> "DIAMOND";
                case NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS -> "NETHERITE";
                default -> null;
            };
            return key == null ? 0D : armorScores.getOrDefault(key, 0D);
        }

        public double weaponScore(Material material) {
            return weaponScores.getOrDefault(material, 0D);
        }
    }

    public record MobGearSettings(
            double qualityJitter,
            ChanceRange armorOverallChance,
            Map<ArmorSlot, ChanceRange> armorSlotChances,
            List<ArmorTier> armorTiers,
            ChanceRange weaponOverallChance,
            List<WeaponChoice> weaponChoices
    ) {
        public ChanceRange armorSlotChance(ArmorSlot slot) {
            return armorSlotChances.get(slot);
        }
    }

    public record ChanceRange(double min, double max) {
        public double valueAt(double difficulty) {
            return min + ((max - min) * clamp(difficulty, 0D, 1D));
        }
    }

    public record ArmorTier(
            double minDifficulty,
            double weight,
            Map<ArmorSlot, Material> pieces
    ) {
    }

    public record WeaponChoice(
            double minDifficulty,
            double weight,
            Material material
    ) {
    }
}
