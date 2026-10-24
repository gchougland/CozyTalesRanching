package com.hexvane.cozytalesranching.species;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /** A colour variant's ranch role to its baby role (Bunny parents have Bunny kits). Optional. */
    public Map<String, String> RanchBabyRoleVariants = Map.of();
    /**
     * A predator is never befriended by walking up with food: it attacks. It is tamed with bait (a Bait Bowl holding one
     * of {@link #BaitItems}); eating it leaves the predator Calm for {@link #CalmMinutes}, and only a calm predator can
     * be fed by hand. The bait system lives in the {@code predator} package.
     */
    public boolean Predator = false;
    public List<String> BaitItems = List.of();
    public double CalmMinutes = 3.0;
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
    /**
     * A look's ranch role (adult) to the products it makes instead of {@link #Products} (a Magma snail makes fire
     * essence). Babies of a look read their adult look's list. Optional; read it through {@link #productsFor}.
     */
    public Map<String, List<Product>> VariantProducts = Map.of();
    /**
     * Rules that give a newborn a look of its own instead of a parent's, tried in order; the first whose condition
     * holds and whose {@code Chance} roll succeeds wins. Optional.
     */
    public List<BirthVariant> BirthVariants = List.of();
    public List<String> HabitatRequires = List.of();
    public List<String> HabitatPrefers = List.of();

    public static final class Product {
        public String Item = "";
        public int Quantity = 1;
        public double IntervalMinutes = 30;
        /** House = collected into the house; Drop = dropped near the animal; Harvest = player uses a tool. */
        public String Collect = "House";
    }

    /**
     * One birth rule. Set {@code Feature} (a habitat feature id present at the house) or {@code Parents} (one or two
     * ranch roles: with two, the pair in either order; with one, either parent having it), or both. {@code Role} is the
     * adult look the baby gets (its baby role comes from {@link #RanchBabyRoleVariants}), with probability {@code Chance}.
     */
    public static final class BirthVariant {
        public String Feature = "";
        public List<String> Parents = List.of();
        public String Role = "";
        public double Chance = 1.0;
    }

    @Nonnull
    public SpeciesDefinition normalise() {
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
        BaitItems = orEmpty(BaitItems);
        if (!(CalmMinutes > 0)) CalmMinutes = 3.0;
        RanchBabyRoleVariants = RanchBabyRoleVariants == null ? Map.of() : Map.copyOf(RanchBabyRoleVariants);
        Map<String, List<Product>> vp = new LinkedHashMap<>();
        if (VariantProducts != null) {
            VariantProducts.forEach((role, list) -> {
                if (role != null && !role.isBlank()) vp.put(role, list == null ? List.of() : list.stream().filter(Objects::nonNull).toList());
            });
        }
        VariantProducts = Collections.unmodifiableMap(vp);
        List<BirthVariant> bv = new ArrayList<>();
        if (BirthVariants != null) {
            for (BirthVariant b : BirthVariants) {
                if (b == null || b.Role == null || b.Role.isBlank()) continue;
                if (b.Feature == null) b.Feature = "";
                b.Parents = orEmpty(b.Parents);
                if (b.Feature.isBlank() && b.Parents.isEmpty()) continue;
                bv.add(b);
            }
        }
        BirthVariants = List.copyOf(bv);
        return this;
    }

    /**
     * The products an animal with ranch role {@code role} makes: its look's {@link #VariantProducts} (a baby look's
     * adult look counts), else {@link #Products}. Null or unknown roles get {@link #Products}.
     */
    @Nonnull
    public List<Product> productsFor(@Nullable String role) {
        if (role != null && !VariantProducts.isEmpty()) {
            List<Product> own = VariantProducts.get(role);
            if (own != null) return own;
            for (Map.Entry<String, String> e : RanchBabyRoleVariants.entrySet()) {
                if (e.getValue().equals(role)) {
                    List<Product> adult = VariantProducts.get(e.getKey());
                    if (adult != null) return adult;
                }
            }
        }
        return Products;
    }

    /** Every product any look of this species makes, each item once, {@link #Products} first. For lists and summaries. */
    @Nonnull
    public List<Product> allProducts() {
        if (VariantProducts.isEmpty()) return Products;
        Map<String, Product> byItem = new LinkedHashMap<>();
        for (Product p : Products) if (p != null && p.Item != null) byItem.putIfAbsent(p.Item, p);
        for (List<Product> list : VariantProducts.values()) for (Product p : list) if (p.Item != null) byItem.putIfAbsent(p.Item, p);
        return List.copyOf(byItem.values());
    }

    /**
     * The ranch role an animal with {@code fromRole} becomes; empty string if the species has none. A baby whose wild or
     * adopt role is a variant ({@code Boar_Piglet} to the adult look {@code CozyRanch_Pig_Boar}) becomes that look's baby
     * ({@link #RanchBabyRoleVariants}); other babies become {@link #RanchBabyRole}.
     */
    @Nonnull
    public String ranchRoleFor(@Nullable String fromRole, boolean baby) {
        if (baby) {
            if (fromRole != null) {
                String adult = RanchRoleVariants.get(fromRole);
                if (adult == null && RanchRoleVariants.containsValue(fromRole)) adult = fromRole;
                String look = adult != null ? RanchBabyRoleVariants.get(adult) : null;
                if (look != null) return look;
                if (RanchBabyRoleVariants.containsValue(fromRole)) return fromRole;
            }
            if (!RanchBabyRole.isEmpty()) return RanchBabyRole;
        }
        if (fromRole != null) {
            String variant = RanchRoleVariants.get(fromRole);
            if (variant != null) return variant;
            if (RanchRoleVariants.containsValue(fromRole)) return fromRole;
        }
        return RanchRole;
    }

    /**
     * The wild role a ranch animal with {@code currentRole} goes back to; empty string if the species has none. A variant
     * baby goes back to the wild baby of its look ({@code CozyRanch_Pig_Boar_Piglet} to {@code Boar_Piglet}), else to the
     * wild role of its adult look.
     */
    @Nonnull
    public String wildRoleFor(@Nullable String currentRole, boolean baby) {
        if (currentRole != null) {
            // walk the role lists in order, so a look with a walking and a flying wild role goes back as the first
            if (baby) {
                for (String wild : WildBabyRoles) {
                    String look = RanchRoleVariants.get(wild);
                    if (look != null && currentRole.equals(RanchBabyRoleVariants.get(look))) return wild;
                }
            }
            for (String wild : WildRoles) {
                if (currentRole.equals(RanchRoleVariants.get(wild))) return wild;
            }
            if (baby && WildBabyRoles.isEmpty()) {
                // no wild babies: a look's baby goes back as the wild adult of that look
                for (Map.Entry<String, String> b : RanchBabyRoleVariants.entrySet()) {
                    if (!b.getValue().equals(currentRole)) continue;
                    for (String wild : WildRoles) {
                        if (b.getKey().equals(RanchRoleVariants.get(wild))) return wild;
                    }
                }
            }
        }
        if (baby && !WildBabyRoles.isEmpty()) return WildBabyRoles.getFirst();
        return WildRoles.isEmpty() ? "" : WildRoles.getFirst();
    }

    private static List<String> orEmpty(List<String> l) {
        return l == null ? List.of() : List.copyOf(l);
    }
}
