package com.hexvane.cozytalesranching.behaviour;

import com.hexvane.cozytalesranching.behaviour.BehaviourConfig.Def;
import com.hexvane.cozytalesranching.behaviour.BehaviourConfig.GroundRule;
import com.hexvane.cozytalesranching.behaviour.BehaviourConfig.Kind;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The planner's rules with no engine: who may act, what it may pick, how often, and personalities. */
final class BehaviourRules {
    static final List<String> PERSONALITIES = List.of("shy", "playful", "curious", "lazy", "affectionate");

    /**
     * Behaviours start only between these day progress values (0 = midnight). The house feature sends residents
     * home at 0.77 and brings them out at 0.23; the margin keeps a behaviour from starting just before dusk.
     */
    static final double DAY_START = 0.25;
    static final double DAY_END = 0.75;

    static final String STATE_IDLE = "Idle";
    static final String STATE_BEHAVE = "Behave";
    static final String STATE_TAG = "Tag";
    static final String STATE_MEET = "Meet";

    enum Verdict {
        OK, NOT_OWNED, NOT_HOUSED, NOT_ACTIVE, FOLLOWING, NOT_IDLE, ROLE_CHANGING, NOT_RANCH_ROLE, NIGHT, OUT_OF_RANGE,
        RECENT_INTERACTION
    }

    /** What the planner knows about one animal at a pass. {@code state} is the base state name (before any dot). */
    record Snapshot(
        boolean owned,
        boolean housed,
        boolean active,
        boolean following,
        @Nonnull String state,
        boolean roleChanging,
        boolean ranchRole,
        boolean daytime,
        double distanceToDoor,
        double homeRange,
        double secondsSinceInteraction
    ) {}

    private BehaviourRules() {}

    static boolean isDaytime(double dayProgress) {
        return dayProgress >= DAY_START && dayProgress < DAY_END;
    }

    static boolean isOwnState(@Nonnull String state) {
        return state.equals(STATE_BEHAVE) || state.equals(STATE_TAG) || state.equals(STATE_MEET);
    }

    /** May a new behaviour start? Only an owned, housed, active, idle resident in its home range by day. */
    @Nonnull
    static Verdict canStart(@Nonnull Snapshot s, @Nonnull BehaviourConfig c) {
        Verdict common = common(s);
        if (common != Verdict.OK) return common;
        if (!s.state().equals(STATE_IDLE)) return Verdict.NOT_IDLE;
        if (s.distanceToDoor() > s.homeRange()) return Verdict.OUT_OF_RANGE;
        if (s.secondsSinceInteraction() < c.RecentInteractionSeconds) return Verdict.RECENT_INTERACTION;
        return Verdict.OK;
    }

    /**
     * May a running behaviour go on? Anything else that took the animal (follow, stay, going home, a role swap,
     * the night) wins, and the behaviour stops without touching the animal's state.
     */
    @Nonnull
    static Verdict canContinue(@Nonnull Snapshot s) {
        Verdict common = common(s);
        if (common != Verdict.OK) return common;
        if (!isOwnState(s.state())) return Verdict.NOT_IDLE;
        if (s.distanceToDoor() > s.homeRange() + 2) return Verdict.OUT_OF_RANGE;
        return Verdict.OK;
    }

    @Nonnull
    private static Verdict common(@Nonnull Snapshot s) {
        if (!s.owned()) return Verdict.NOT_OWNED;
        if (!s.housed()) return Verdict.NOT_HOUSED;
        if (!s.active()) return Verdict.NOT_ACTIVE;
        if (s.following()) return Verdict.FOLLOWING;
        if (!s.ranchRole()) return Verdict.NOT_RANCH_ROLE;
        if (s.roleChanging()) return Verdict.ROLE_CHANGING;
        if (!s.daytime()) return Verdict.NIGHT;
        return Verdict.OK;
    }

    /** What is around the animal when it decides. */
    record Context(
        @Nonnull String speciesId,
        boolean baby,
        @Nullable String personality,
        @Nonnull Set<String> modelAnimations,
        @Nonnull Set<String> groundTags,
        boolean shaded,
        boolean nearHouse,
        boolean troughNear,
        @Nonnull Set<String> toysNear,
        boolean parentOut,
        boolean partnerNear,
        boolean playerNear,
        boolean ownerNear
    ) {}

    /** A behaviour the animal can do now, its weight, and the clip to play (null: stand still). */
    record Option(@Nonnull String id, double weight, @Nullable String animation) {}

    /** Every behaviour the species can do here and now, with personality weights, minus those cooling down. */
    @Nonnull
    static List<Option> options(@Nonnull Context ctx, @Nonnull BehaviourConfig c, @Nonnull Predicate<String> coolingDown) {
        List<Option> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : c.behavioursOf(ctx.speciesId(), ctx.baby()).entrySet()) {
            String id = e.getKey();
            Def def = c.def(id);
            if (def == null || def.Weight <= 0 || def.kind() == Kind.RETREAT) continue;
            if (def.OnlyPersonality != null && !def.OnlyPersonality.equals(ctx.personality())) continue;
            if (!available(id, def, ctx)) continue;
            String clip = animationFor(e.getValue(), ctx.modelAnimations());
            if (clip == null && !def.AllowStill && !e.getValue().isEmpty()) continue;
            if (coolingDown.test(id)) continue;
            double w = def.Weight * personalityMultiplier(c, ctx.personality(), id);
            if (w > 0) out.add(new Option(id, w, clip));
        }
        return out;
    }

    /** Whether the spot or company the behaviour needs is there. */
    static boolean available(@Nonnull String id, @Nonnull Def def, @Nonnull Context ctx) {
        return switch (def.kind()) {
            case HERE -> def.Ground == null || def.Ground.isEmpty() || ctx.groundTags().contains(def.Ground);
            case TROUGH -> ctx.troughNear();
            case TOY -> ctx.toysNear().contains(id);
            case PARENT -> ctx.baby() && ctx.parentOut();
            case SOCIAL -> ctx.partnerNear();
            case REST -> ctx.shaded() || ctx.nearHouse();
            case PLAYER -> ctx.playerNear();
            case OWNER -> ctx.ownerNear();
            case RETREAT -> false;
        };
    }

    /** The first listed clip the model has; null if none (or none listed). */
    @Nullable
    static String animationFor(@Nonnull List<String> clips, @Nonnull Set<String> modelAnimations) {
        for (String clip : clips) if (modelAnimations.contains(clip)) return clip;
        return null;
    }

    static double personalityMultiplier(@Nonnull BehaviourConfig c, @Nullable String personality, @Nonnull String id) {
        if (personality == null) return 1.0;
        BehaviourConfig.Personality p = c.Personalities.get(personality);
        if (p == null) return 1.0;
        Double exact = p.Weights.get(id);
        if (exact != null) return Math.max(0, exact);
        for (Map.Entry<String, Double> e : p.Weights.entrySet()) {
            String key = e.getKey();
            if (key.endsWith("*") && id.startsWith(key.substring(0, key.length() - 1)) && e.getValue() != null) return Math.max(0, e.getValue());
        }
        return 1.0;
    }

    static double idleWeight(@Nonnull BehaviourConfig c, @Nullable String personality) {
        BehaviourConfig.Personality p = personality == null ? null : c.Personalities.get(personality);
        return p != null && p.IdleWeight > 0 ? p.IdleWeight : c.IdleWeight;
    }

    /** Picks by weight; {@code roll} in [0, 1). Null means "nothing special this time". */
    @Nullable
    static Option pick(@Nonnull List<Option> options, double idleWeight, double roll) {
        double total = Math.max(0, idleWeight);
        for (Option o : options) total += o.weight();
        if (total <= 0 || options.isEmpty()) return null;
        double at = roll * total;
        for (Option o : options) {
            at -= o.weight();
            if (at < 0) return o;
        }
        return null;
    }

    /** Seconds until the animal's next decision; {@code roll} in [0, 1). */
    static double decisionSeconds(@Nonnull BehaviourConfig c, @Nullable String personality, double roll) {
        double base = c.DecisionSeconds[0] + (c.DecisionSeconds[1] - c.DecisionSeconds[0]) * roll;
        BehaviourConfig.Personality p = personality == null ? null : c.Personalities.get(personality);
        return base * (p != null ? p.DecisionScale : 1.0);
    }

    /** How long a behaviour lasts; {@code roll} in [0, 1). */
    static double durationSeconds(@Nonnull Def def, double roll) {
        return def.Seconds[0] + (def.Seconds[1] - def.Seconds[0]) * roll;
    }

    /** One of the five, uniformly; {@code roll} in [0, 1). */
    @Nonnull
    static String rollPersonality(double roll) {
        int i = (int) Math.floor(Math.clamp(roll, 0.0, 0.999999) * PERSONALITIES.size());
        return PERSONALITIES.get(i);
    }

    /** The cooldown between two starts of the same behaviour by one animal. */
    static long cooldownMs(@Nonnull Def def, double dayLengthSeconds) {
        if (def.CooldownDayFraction > 0 && dayLengthSeconds > 0) return (long) (def.CooldownDayFraction * dayLengthSeconds * 1000);
        return (long) (def.CooldownSeconds * 1000);
    }

    static boolean coolingDown(@Nullable Long lastStartMs, long cooldownMs, long nowMs) {
        return lastStartMs != null && nowMs - lastStartMs < cooldownMs;
    }

    /** The contract's limit: at most one event per animal per behaviour per {@code EventCooldownSeconds}. */
    static boolean eventAllowed(@Nullable Long lastEventMs, @Nonnull BehaviourConfig c, long nowMs) {
        return lastEventMs == null || nowMs - lastEventMs >= (long) (c.EventCooldownSeconds * 1000);
    }

    /** What the planner read around the animal: base block ids (null for empty) and fluid ids. */
    record GroundSample(
        @Nullable String under,
        @Nullable String at,
        @Nullable String fluidAt,
        @Nullable String fluidUnder,
        @Nonnull Set<String> nearBlocks,
        @Nonnull Set<String> nearFluids
    ) {}

    /** The ground tags (grass, dust, mud, pond...) the sample meets. */
    @Nonnull
    static Set<String> groundTags(@Nonnull GroundSample g, @Nonnull BehaviourConfig c) {
        Set<String> out = new LinkedHashSet<>();
        for (Map.Entry<String, List<GroundRule>> e : c.Ground.entrySet()) {
            for (GroundRule r : e.getValue()) {
                if (matches(r, g)) {
                    out.add(e.getKey());
                    break;
                }
            }
        }
        return out;
    }

    static boolean matches(@Nonnull GroundRule r, @Nonnull GroundSample g) {
        if (!r.Under.isEmpty() && !blockMatches(g.under(), r.Under, r.Exclude)) return false;
        if (!r.At.isEmpty() && !blockMatches(g.at(), r.At, r.Exclude)) return false;
        if (!r.Near.isEmpty() && g.nearBlocks().stream().noneMatch(b -> blockMatches(b, r.Near, r.Exclude))) return false;
        if (!r.Fluid.isEmpty() && !(r.Fluid.contains(g.fluidAt()) || r.Fluid.contains(g.fluidUnder()))) return false;
        if (!r.NearFluid.isEmpty() && g.nearFluids().stream().noneMatch(r.NearFluid::contains)) return false;
        return !(r.Under.isEmpty() && r.At.isEmpty() && r.Near.isEmpty() && r.Fluid.isEmpty() && r.NearFluid.isEmpty());
    }

    private static boolean blockMatches(@Nullable String id, @Nonnull List<String> prefixes, @Nonnull List<String> exclude) {
        if (id == null) return false;
        boolean hit = false;
        for (String p : prefixes) {
            if (id.startsWith(p)) {
                hit = true;
                break;
            }
        }
        if (!hit) return false;
        for (String e : exclude) if (!e.isEmpty() && id.contains(e)) return false;
        return true;
    }
}
