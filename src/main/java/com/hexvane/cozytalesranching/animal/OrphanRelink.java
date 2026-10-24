package com.hexvane.cozytalesranching.animal;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Matches an untagged entity of a ranch or adopt role to the MISSING record it most likely is.
 * <p>
 * Such an entity is one of ours that came back without its tag: the vanilla coop
 * ({@code CoopBlock.ensureSpawnResidentsInWorld}) despawns its residents at dusk and spawns fresh
 * copies from the stored role id at dawn or when the coop is broken, with new UUIDs and none of our
 * components. Any other mod that stores and re-creates NPCs would do the same. Pure logic, no engine
 * types, so it is unit tested.
 */
final class OrphanRelink {
    /** How far (horizontally) the entity may be from where the record was last seen. Coop capture radius is 10; residents spawn 3 blocks out and wander. */
    static final double RADIUS = 48.0;

    private OrphanRelink() {}

    /**
     * The nearest MISSING record of this species, world and age within {@link #RADIUS} of (x, z) whose
     * known role is the entity's role (a coop re-creates the exact role it stored). A record with no
     * known role matches any role of its species. Null when none fits.
     */
    @Nullable
    static AnimalRecord match(@Nonnull AnimalStore store, @Nonnull String worldName, @Nonnull String speciesId,
                              @Nonnull String role, boolean baby, double x, double z) {
        List<AnimalRecord> inWorld = store.inWorld(worldName);
        AnimalRecord best = null;
        double bestSq = RADIUS * RADIUS;
        for (AnimalRecord r : inWorld) {
            if (r.state != AnimalRecord.State.MISSING) continue;
            if (!speciesId.equals(r.speciesId) || r.baby != baby) continue;
            if (!roleFits(role, store.role(r.animalId), r.spawnRole)) continue;
            double dx = r.lastX - x, dz = r.lastZ - z;
            double d = dx * dx + dz * dz;
            if (d <= bestSq) {
                bestSq = d;
                best = r;
            }
        }
        return best;
    }

    static boolean roleFits(@Nonnull String entityRole, @Nullable String lastRole, @Nullable String spawnRole) {
        boolean noLast = lastRole == null || lastRole.isEmpty();
        boolean noSpawn = spawnRole == null || spawnRole.isEmpty();
        if (noLast && noSpawn) return true;
        return Objects.equals(entityRole, lastRole) || Objects.equals(entityRole, spawnRole);
    }
}
