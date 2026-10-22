package com.hexvane.cozytalesranching.species;

import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One supported species, read with Gson from {@code Server/CozyTalesRanching/Species/<Id>.json} in the jar,
 * or from {@code Species/<Id>.json} in the plugin data directory (a file there replaces the bundled one).
 * Field names are the JSON keys. Lists are never null after {@link #normalise()}.
 */
public final class SpeciesDefinition {
    public String Id = "";
    public String NameKey = "";
    /** Wild NPC roles a player can befriend. */
    public List<String> WildRoles = List.of();
    public List<String> WildBabyRoles = List.of();
    /** Roles of animals tamed before this mod (vanilla Tamed_*), adopted on sight when AdoptVanillaTamed is on. */
    public List<String> AdoptRoles = List.of();
    public List<String> AdoptBabyRoles = List.of();
    /** This mod's own roles for ranch animals. */
    public String RanchRole = "";
    public String RanchBabyRole = "";
    /** Wild or adopt role to the ranch role it becomes, for looks that must survive taming (frog colours). Adults only. */
    public Map<String, String> RanchRoleVariants = Map.of();
    public String HouseBlock = "";
    public int HouseCapacity = 4;
    public List<String> BasicFoods = List.of();
    public List<String> PreferredFoods = List.of();
    public List<String> FavoriteTreats = List.of();
    public int FeedingsToTame = 3;
    /** 0 = walks up to you, 1 = very skittish. Taming reads it for approach distance. */
    public double Shyness = 0.2;
    public double GrowUpMinutes = 60;
    public double BreedCooldownMinutes = 40;
    public List<Product> Products = List.of();
    public List<String> HabitatRequires = List.of();
    public List<String> HabitatPrefers = List.of();

    public static final class Product {
        public String Item = "";
        public int Quantity = 1;
        public double IntervalMinutes = 30;
        /** House = collected into the house; Drop = dropped near the animal; Harvest = player uses a tool. */
        public String Collect = "House";
    }

    @Nonnull
    SpeciesDefinition normalise() {
        WildRoles = orEmpty(WildRoles);
        WildBabyRoles = orEmpty(WildBabyRoles);
        AdoptRoles = orEmpty(AdoptRoles);
        AdoptBabyRoles = orEmpty(AdoptBabyRoles);
        BasicFoods = orEmpty(BasicFoods);
        PreferredFoods = orEmpty(PreferredFoods);
        FavoriteTreats = orEmpty(FavoriteTreats);
        Products = Products == null ? List.of() : List.copyOf(Products);
        HabitatRequires = orEmpty(HabitatRequires);
        HabitatPrefers = orEmpty(HabitatPrefers);
        RanchRoleVariants = RanchRoleVariants == null ? Map.of() : Map.copyOf(RanchRoleVariants);
        return this;
    }

    /** The ranch role an animal with {@code fromRole} becomes; empty string if the species has none. */
    @Nonnull
    public String ranchRoleFor(@Nullable String fromRole, boolean baby) {
        if (baby && !RanchBabyRole.isEmpty()) return RanchBabyRole;
        if (fromRole != null) {
            String variant = RanchRoleVariants.get(fromRole);
            if (variant != null) return variant;
        }
        return RanchRole;
    }

    /** The wild role a ranch animal with {@code currentRole} goes back to; empty string if the species has none. */
    @Nonnull
    public String wildRoleFor(@Nullable String currentRole, boolean baby) {
        if (currentRole != null) {
            for (Map.Entry<String, String> e : RanchRoleVariants.entrySet()) {
                if (e.getValue().equals(currentRole) && WildRoles.contains(e.getKey())) return e.getKey();
            }
        }
        if (baby && !WildBabyRoles.isEmpty()) return WildBabyRoles.getFirst();
        return WildRoles.isEmpty() ? "" : WildRoles.getFirst();
    }

    private static List<String> orEmpty(List<String> l) {
        return l == null ? List.of() : List.copyOf(l);
    }
}
