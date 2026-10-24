package com.hexvane.cozytalesranching.predator;

import com.hexvane.cozytalesranching.predator.CalmRules.Phase;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** What taming asks of the predator feature. Safe to call before the feature is set up (everything is then "not calm"). */
public final class PredatorApi {
    private PredatorApi() {}

    /** The predator's phase, or null when it is not lured, calm or spooked. Any thread. */
    @Nullable
    public static Phase phase(@Nullable UUID entity) {
        Predators p = Predators.get();
        return p == null ? null : p.book.phase(entity);
    }

    /** What hand feeding this wild animal may do. */
    @Nonnull
    public static CalmRules.FeedGate feedGate(@Nonnull SpeciesDefinition species, @Nullable UUID entity) {
        return CalmRules.feedGate(species, phase(entity));
    }

    /** A calm predator took food from a hand: Calm is topped up to a full CalmMinutes. World thread. */
    public static void handFed(@Nonnull UUID entity, @Nonnull SpeciesDefinition species) {
        Predators p = Predators.get();
        if (p != null) p.book.extend(entity, CalmRules.calmUntil(System.currentTimeMillis(), species));
    }

    /** A calm predator was startled while being fed (a hurried player): it flees. World thread. */
    public static void startle(@Nonnull World world, @Nonnull UUID entity, @Nullable UUID player) {
        Predators p = Predators.get();
        if (p != null) p.startle(world, entity, player);
    }

    /** The predator became a ranch animal: forget its Calm without touching the entity. Any thread. */
    public static void tamed(@Nonnull UUID entity) {
        Predators p = Predators.get();
        if (p != null) p.tamed(entity);
    }
}
