package com.hexvane.cozytalesranching.breeding;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Every inheritable trait and the inheritance odds, read with Gson from {@code Server/CozyTalesRanching/Traits.json}
 * (or {@code Traits.json} in the plugin data directory, which replaces it). Field names are the JSON keys.
 * Effects are free-form numbers per trait; the keys a system reads are the constants below.
 */
public final class TraitBook {
    public static final String BREED_COOLDOWN_MULTIPLIER = "BreedCooldownMultiplier";
    public static final String GROW_UP_MULTIPLIER = "GrowUpMultiplier";
    public static final String TWIN_CHANCE = "TwinChance";

    public double InheritChance = 0.5;
    public double MutationChance = 0.08;
    public double TamedTraitChance = 0.25;
    public int MaxTraits = 2;
    public List<Trait> Traits = List.of();

    public static final class Trait {
        public String Id = "";
        public String NameKey = "";
        public String DescKey = "";
        public Map<String, Double> Effects = Map.of();
    }

    private transient Map<String, Trait> byId = Map.of();

    @Nonnull
    public TraitBook normalise() {
        InheritChance = Math.clamp(InheritChance, 0.0, 1.0);
        MutationChance = Math.clamp(MutationChance, 0.0, 1.0);
        TamedTraitChance = Math.clamp(TamedTraitChance, 0.0, 1.0);
        MaxTraits = Math.max(0, MaxTraits);
        Map<String, Trait> ids = new LinkedHashMap<>();
        if (Traits != null) {
            for (Trait t : Traits) {
                if (t == null || t.Id == null || t.Id.isBlank()) continue;
                if (t.Effects == null) t.Effects = Map.of();
                ids.put(t.Id, t);
            }
        }
        Traits = List.copyOf(ids.values());
        byId = Map.copyOf(ids);
        return this;
    }

    @Nonnull
    public Optional<Trait> get(@Nonnull String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean has(@Nonnull String id) {
        return byId.containsKey(id);
    }

    @Nonnull
    public List<String> ids() {
        return Traits.stream().map(t -> t.Id).toList();
    }

    /** Product of the effect over the traits that have it; 1 when none do. Unknown trait ids count as nothing. */
    public double multiplier(@Nonnull Collection<String> traits, @Nonnull String effect) {
        double m = 1.0;
        for (String id : traits) {
            Trait t = byId.get(id);
            if (t == null) continue;
            Double v = t.Effects.get(effect);
            if (v != null && v > 0) m *= v;
        }
        return m;
    }

    /** Chance that at least one of the traits fires: {@code 1 - prod(1 - p)}. */
    public double chance(@Nonnull Collection<String> traits, @Nonnull String effect) {
        double none = 1.0;
        for (String id : traits) {
            Trait t = byId.get(id);
            if (t == null) continue;
            Double v = t.Effects.get(effect);
            if (v != null) none *= 1.0 - Math.clamp(v, 0.0, 1.0);
        }
        return 1.0 - none;
    }
}
