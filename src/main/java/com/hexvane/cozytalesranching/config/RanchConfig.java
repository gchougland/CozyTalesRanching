package com.hexvane.cozytalesranching.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Server configuration, saved as {@code RanchConfig.json} in the plugin data directory. */
public final class RanchConfig {
    private String[] tamingBlockedWorlds = new String[0];
    private boolean adoptVanillaTamed = true;
    private String[] extraAdoptRoles = new String[0];
    private String[] ignoredRolePrefixes = {"MD_Familiar_", "Endgame_Pet_", "Averion", "Mount_"};
    private String[] ignoredComponentClasses = {
        "endgame.plugin.components.PetOwnerComponent",
        "com.danbagh.dungeonframework.features.familiars.component.FamiliarComponent",
        "com.danbagh.dungeonframework.features.mounts.SummonedMountComponent",
        "com.alechilles.alecstamework.companion.live.TameworkCompanionComponent",
        "com.stonefallstudio.mount.MountOwnerComponent",
        "com.airijko.endlessleveling.ecs.components.SummonOwnerComponent",
        "com.zenkuro.arcanum.spell.builtin.summon.SummonOwnershipComponent"
    };
    private String[] disabledSpecies = new String[0];
    private boolean protectOwnedAnimals = true;
    private boolean protectFromHostileMobs = true;
    private boolean breedingEnabled = true;
    private double followTeleportDistance = 24.0;
    private double followStuckSeconds = 6.0;
    private double habitatScanIntervalSeconds = 120.0;
    private int habitatScanRadius = 12;
    private int maxAnimalsPerPlayer = 64;
    private double saveDebounceSeconds = 10.0;
    private int recoveryDelaySeconds = 60;
    private int homeRangeRadius = 10;
    private int troughRange = 8;
    private int breedingMinHearts = 3;
    private double breedingMinHappiness = 0.7;
    private boolean showNameplates = true;
    private boolean debugLogging = false;
    /** With QuestLinesClaims installed, houses inside a claim follow the claim's permissions. */
    private boolean claimsIntegration = true;

    @Nonnull
    public static final BuilderCodec<RanchConfig> CODEC = BuilderCodec.builder(RanchConfig.class, RanchConfig::new)
        .append(new KeyedCodec<>("TamingBlockedWorlds", Codec.STRING_ARRAY), (c, v) -> c.tamingBlockedWorlds = v, c -> c.tamingBlockedWorlds).add()
        .append(new KeyedCodec<>("AdoptVanillaTamed", Codec.BOOLEAN), (c, v) -> c.adoptVanillaTamed = v, c -> c.adoptVanillaTamed).add()
        .append(new KeyedCodec<>("ExtraAdoptRoles", Codec.STRING_ARRAY), (c, v) -> c.extraAdoptRoles = v, c -> c.extraAdoptRoles).add()
        .append(new KeyedCodec<>("IgnoredRolePrefixes", Codec.STRING_ARRAY), (c, v) -> c.ignoredRolePrefixes = v, c -> c.ignoredRolePrefixes).add()
        .append(new KeyedCodec<>("IgnoredComponentClasses", Codec.STRING_ARRAY), (c, v) -> c.ignoredComponentClasses = v, c -> c.ignoredComponentClasses).add()
        .append(new KeyedCodec<>("DisabledSpecies", Codec.STRING_ARRAY), (c, v) -> c.disabledSpecies = v, c -> c.disabledSpecies).add()
        .append(new KeyedCodec<>("ProtectOwnedAnimals", Codec.BOOLEAN), (c, v) -> c.protectOwnedAnimals = v, c -> c.protectOwnedAnimals).add()
        .append(new KeyedCodec<>("ProtectFromHostileMobs", Codec.BOOLEAN), (c, v) -> c.protectFromHostileMobs = v, c -> c.protectFromHostileMobs).add()
        .append(new KeyedCodec<>("BreedingEnabled", Codec.BOOLEAN), (c, v) -> c.breedingEnabled = v, c -> c.breedingEnabled).add()
        .append(new KeyedCodec<>("FollowTeleportDistance", Codec.DOUBLE), (c, v) -> c.followTeleportDistance = v, c -> c.followTeleportDistance).add()
        .append(new KeyedCodec<>("FollowStuckSeconds", Codec.DOUBLE), (c, v) -> c.followStuckSeconds = v, c -> c.followStuckSeconds).add()
        .append(new KeyedCodec<>("HabitatScanIntervalSeconds", Codec.DOUBLE), (c, v) -> c.habitatScanIntervalSeconds = v, c -> c.habitatScanIntervalSeconds).add()
        .append(new KeyedCodec<>("HabitatScanRadius", Codec.INTEGER), (c, v) -> c.habitatScanRadius = v, c -> c.habitatScanRadius).add()
        .append(new KeyedCodec<>("MaxAnimalsPerPlayer", Codec.INTEGER), (c, v) -> c.maxAnimalsPerPlayer = v, c -> c.maxAnimalsPerPlayer).add()
        .append(new KeyedCodec<>("SaveDebounceSeconds", Codec.DOUBLE), (c, v) -> c.saveDebounceSeconds = v, c -> c.saveDebounceSeconds).add()
        .append(new KeyedCodec<>("RecoveryDelaySeconds", Codec.INTEGER), (c, v) -> c.recoveryDelaySeconds = v, c -> c.recoveryDelaySeconds).add()
        .append(new KeyedCodec<>("HomeRangeRadius", Codec.INTEGER), (c, v) -> c.homeRangeRadius = v, c -> c.homeRangeRadius).add()
        .append(new KeyedCodec<>("TroughRange", Codec.INTEGER), (c, v) -> c.troughRange = v, c -> c.troughRange).add()
        .append(new KeyedCodec<>("BreedingMinHearts", Codec.INTEGER), (c, v) -> c.breedingMinHearts = v, c -> c.breedingMinHearts).add()
        .append(new KeyedCodec<>("BreedingMinHappiness", Codec.DOUBLE), (c, v) -> c.breedingMinHappiness = v, c -> c.breedingMinHappiness).add()
        .append(new KeyedCodec<>("ShowNameplates", Codec.BOOLEAN), (c, v) -> c.showNameplates = v, c -> c.showNameplates).add()
        .append(new KeyedCodec<>("DebugLogging", Codec.BOOLEAN), (c, v) -> c.debugLogging = v, c -> c.debugLogging).add()
        .append(new KeyedCodec<>("ClaimsIntegration", Codec.BOOLEAN), (c, v) -> c.claimsIntegration = v, c -> c.claimsIntegration).add()
        .build();

    @Nullable
    private static volatile RanchConfig instance;

    @Nonnull
    public static RanchConfig get() {
        RanchConfig c = instance;
        return c != null ? c : new RanchConfig();
    }

    public static void bind(@Nonnull RanchConfig config) {
        instance = config;
    }

    /** World names compare case-insensitively. */
    public boolean isTamingBlocked(@Nonnull String worldName) {
        String w = worldName.toLowerCase(Locale.ROOT);
        for (String blocked : tamingBlockedWorlds) {
            if (blocked != null && blocked.toLowerCase(Locale.ROOT).equals(w)) return true;
        }
        return false;
    }

    public boolean isRoleIgnored(@Nonnull String roleId) {
        for (String prefix : ignoredRolePrefixes) {
            if (prefix != null && !prefix.isEmpty() && roleId.startsWith(prefix)) return true;
        }
        return false;
    }

    public boolean isSpeciesEnabled(@Nonnull String speciesId) {
        for (String s : disabledSpecies) {
            if (speciesId.equalsIgnoreCase(s)) return false;
        }
        return true;
    }

    @Nonnull public List<String> tamingBlockedWorlds() { return List.of(tamingBlockedWorlds); }
    public boolean adoptVanillaTamed() { return adoptVanillaTamed; }
    /** Entries are {@code Role=speciesId}, e.g. {@code Tamed_Cow_Highland=cow}. Malformed entries are skipped. */
    @Nonnull public Map<String, String> extraAdoptRoles() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String entry : extraAdoptRoles) {
            if (entry == null) continue;
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) continue;
            out.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim());
        }
        return out;
    }
    @Nonnull public Set<String> ignoredComponentClasses() { return java.util.Arrays.stream(ignoredComponentClasses).collect(Collectors.toUnmodifiableSet()); }
    public boolean protectOwnedAnimals() { return protectOwnedAnimals; }
    public boolean protectFromHostileMobs() { return protectFromHostileMobs; }
    public boolean breedingEnabled() { return breedingEnabled; }
    public double followTeleportDistance() { return Math.max(8.0, followTeleportDistance); }
    public double followStuckSeconds() { return Math.max(2.0, followStuckSeconds); }
    public double habitatScanIntervalSeconds() { return Math.max(15.0, habitatScanIntervalSeconds); }
    public int habitatScanRadius() { return Math.clamp(habitatScanRadius, 4, 24); }
    public int maxAnimalsPerPlayer() { return Math.max(1, maxAnimalsPerPlayer); }
    public double saveDebounceSeconds() { return Math.max(1.0, saveDebounceSeconds); }
    public int recoveryDelaySeconds() { return Math.max(5, recoveryDelaySeconds); }
    /** At least 8: the animal roles wander 6 blocks from the door, and a smaller range sends them home all day. */
    public int homeRangeRadius() { return Math.clamp(homeRangeRadius, 8, 32); }
    public int troughRange() { return Math.clamp(troughRange, 2, 16); }
    public int breedingMinHearts() { return Math.clamp(breedingMinHearts, 0, 5); }
    public double breedingMinHappiness() { return Math.clamp(breedingMinHappiness, 0.0, 1.0); }
    /** Owned animals show their name (or species name) above their head. */
    public boolean showNameplates() { return showNameplates; }
    public boolean debugLogging() { return debugLogging; }
    public boolean claimsIntegration() { return claimsIntegration; }
}
