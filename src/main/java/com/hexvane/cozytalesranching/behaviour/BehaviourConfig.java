package com.hexvane.cozytalesranching.behaviour;

import com.google.gson.Gson;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@code Server/CozyTalesRanching/Behaviour.json}: what residents do by day, how often, and which personality
 * changes what. Field names are the file format. Pure data; no engine types.
 */
public final class BehaviourConfig {
    private static final Gson GSON = new Gson();

    public double[] DecisionSeconds = {25, 70};
    /** Weight of doing nothing special at a decision (the animal keeps wandering). */
    public double IdleWeight = 2.0;
    public double EventCooldownSeconds = 60;
    public double WitnessRange = 16;
    public double MaxWalkSeconds = 15;
    public double ArriveDistance = 1.6;
    /** A walk that times out this close to its spot still plays the behaviour. */
    public double ActAnywayDistance = 3.0;
    public double SocialRange = 8;
    public double PlayerRange = 12;
    public double ShyDistance = 6;
    public double ShyCooldownSeconds = 12;
    public double NearHouseDistance = 4;
    public int ShadeHeight = 6;
    public int ToyScanRadius = 10;
    public double ToyScanSeconds = 60;
    public double RecentInteractionSeconds = 10;
    public boolean Log;
    /** Ground tag to the rules that make it (any rule matching is enough). */
    public Map<String, List<GroundRule>> Ground = new LinkedHashMap<>();
    /** Toy block id (base id, no state) to its behaviour id. */
    public Map<String, String> Toys = new LinkedHashMap<>();
    public Map<String, Def> Behaviours = new LinkedHashMap<>();
    public Map<String, SpeciesEntry> Species = new LinkedHashMap<>();
    public Map<String, Personality> Personalities = new LinkedHashMap<>();

    /** How a behaviour picks its spot. */
    public enum Kind {
        /** where the animal stands (needs {@link Def#Ground} if set) */
        HERE,
        /** beside a trough with food in the house's trough range */
        TROUGH,
        /** beside a toy block the species likes */
        TOY,
        /** a baby walks behind its parent */
        PARENT,
        /** two residents walk up to each other */
        SOCIAL,
        /** a nap in shade, or near the house */
        REST,
        /** walks toward the nearest player */
        PLAYER,
        /** walks to its owner */
        OWNER,
        /** steps away from a stranger (a reaction, never picked by weight) */
        RETREAT
    }

    public static final class Def {
        public String Kind = "here";
        @Nullable public String Ground;
        public double Weight = 1;
        public double CooldownSeconds = 60;
        /** If above 0, the cooldown is this share of the world's day length instead (day plus night). */
        public double CooldownDayFraction;
        public double[] Seconds = {3, 5};
        /** One-shot clips are played again this often while the behaviour lasts; 0 plays once. */
        public double RepeatSeconds;
        /** Allowed even when the model has none of the listed clips (stands still). */
        public boolean AllowStill;
        /** Fires {@code AnimalBehaviourEvent}. Off for the personality moves, which are not contract ids. */
        public boolean Event = true;
        /** Only animals with this personality can pick it. */
        @Nullable public String OnlyPersonality;

        @Nonnull
        public BehaviourConfig.Kind kind() {
            try {
                return BehaviourConfig.Kind.valueOf(Kind.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (RuntimeException e) {
                return BehaviourConfig.Kind.HERE;
            }
        }
    }

    public static final class SpeciesEntry {
        /** Behaviour id to the animation sets to try, in order. */
        public Map<String, List<String>> Adult = new LinkedHashMap<>();
        public Map<String, List<String>> Baby = new LinkedHashMap<>();

        @Nonnull
        public Map<String, List<String>> forAge(boolean baby) {
            return baby ? Baby : Adult;
        }
    }

    public static final class Personality {
        /** Behaviour id (or a prefix ending in {@code *}) to a weight multiplier. */
        public Map<String, Double> Weights = new LinkedHashMap<>();
        /** Multiplies the time between decisions (above 1 = less often). */
        public double DecisionScale = 1.0;
        /** Replaces {@link BehaviourConfig#IdleWeight} when set (0 or below = not set). */
        public double IdleWeight;
    }

    /** One way a ground tag can be met; every condition given must hold. Ids are base ids, matched by prefix. */
    public static final class GroundRule {
        public List<String> Under = new ArrayList<>();
        public List<String> At = new ArrayList<>();
        /** Block ids beside the animal (four sides, at its feet). */
        public List<String> Near = new ArrayList<>();
        /** Fluid at the animal's feet or below. */
        public List<String> Fluid = new ArrayList<>();
        /** Fluid beside the animal (four sides, at its feet or below). */
        public List<String> NearFluid = new ArrayList<>();
        /** A matched block id containing any of these does not count. */
        public List<String> Exclude = new ArrayList<>();

        @Nonnull
        GroundRule normalise() {
            if (Under == null) Under = new ArrayList<>();
            if (At == null) At = new ArrayList<>();
            if (Near == null) Near = new ArrayList<>();
            if (Fluid == null) Fluid = new ArrayList<>();
            if (NearFluid == null) NearFluid = new ArrayList<>();
            if (Exclude == null) Exclude = new ArrayList<>();
            return this;
        }
    }

    @Nullable
    public Def def(@Nonnull String behaviourId) {
        return Behaviours.get(behaviourId);
    }

    /** The behaviours (with clips) this species can do at this age; empty for an unknown species. */
    @Nonnull
    public Map<String, List<String>> behavioursOf(@Nonnull String speciesId, boolean baby) {
        SpeciesEntry s = Species.get(speciesId);
        return s == null ? Map.of() : s.forAge(baby);
    }

    /** The toy behaviour of a block id (state suffix and leading {@code *} ignored), or null. */
    @Nullable
    public String toyBehaviour(@Nonnull String blockId) {
        return Toys.get(baseBlockId(blockId));
    }

    @Nonnull
    public static BehaviourConfig read(@Nonnull Reader reader) {
        BehaviourConfig c = GSON.fromJson(reader, BehaviourConfig.class);
        return (c == null ? new BehaviourConfig() : c).normalise();
    }

    @Nonnull
    BehaviourConfig normalise() {
        if (Ground == null) Ground = new LinkedHashMap<>();
        if (Toys == null) Toys = new LinkedHashMap<>();
        if (Behaviours == null) Behaviours = new LinkedHashMap<>();
        if (Species == null) Species = new LinkedHashMap<>();
        if (Personalities == null) Personalities = new LinkedHashMap<>();
        Ground.values().removeIf(l -> l == null);
        Ground.values().forEach(l -> {
            l.removeIf(r -> r == null);
            l.forEach(GroundRule::normalise);
        });
        Behaviours.values().removeIf(d -> d == null);
        for (Def d : Behaviours.values()) {
            d.Seconds = range(d.Seconds, 1, 120);
            d.Weight = Math.max(0, d.Weight);
            d.CooldownSeconds = Math.max(0, d.CooldownSeconds);
            d.CooldownDayFraction = Math.clamp(d.CooldownDayFraction, 0.0, 1.0);
            d.RepeatSeconds = Math.max(0, d.RepeatSeconds);
            if (d.Kind == null) d.Kind = "here";
        }
        Species.values().removeIf(s -> s == null);
        for (SpeciesEntry s : Species.values()) {
            if (s.Adult == null) s.Adult = new LinkedHashMap<>();
            if (s.Baby == null) s.Baby = new LinkedHashMap<>();
            s.Adult.replaceAll((k, v) -> v == null ? List.of() : v);
            s.Baby.replaceAll((k, v) -> v == null ? List.of() : v);
        }
        Personalities.values().removeIf(p -> p == null);
        for (Personality p : Personalities.values()) {
            if (p.Weights == null) p.Weights = new LinkedHashMap<>();
            p.DecisionScale = Math.clamp(p.DecisionScale, 0.25, 4.0);
        }
        DecisionSeconds = range(DecisionSeconds, 5, 600);
        IdleWeight = Math.max(0, IdleWeight);
        EventCooldownSeconds = Math.max(0, EventCooldownSeconds);
        MaxWalkSeconds = Math.clamp(MaxWalkSeconds, 3, 60);
        ArriveDistance = Math.clamp(ArriveDistance, 0.5, 4);
        ActAnywayDistance = Math.max(ArriveDistance, ActAnywayDistance);
        ShadeHeight = Math.clamp(ShadeHeight, 1, 16);
        ToyScanRadius = Math.clamp(ToyScanRadius, 2, 24);
        ToyScanSeconds = Math.max(10, ToyScanSeconds);
        return this;
    }

    @Nonnull
    private static double[] range(@Nullable double[] r, double min, double max) {
        if (r == null || r.length == 0) return new double[] {min, min};
        double a = Math.clamp(r[0], min, max);
        double b = r.length > 1 ? Math.clamp(r[1], a, max) : a;
        return new double[] {a, b};
    }

    /** {@code *CozyRanch_Toy_Ball_State_Definitions_X} becomes {@code CozyRanch_Toy_Ball}. */
    @Nonnull
    public static String baseBlockId(@Nonnull String id) {
        String s = id.startsWith("*") ? id.substring(1) : id;
        int state = s.indexOf("_State_Definitions_");
        return state >= 0 ? s.substring(0, state) : s;
    }
}
