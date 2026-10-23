package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.api.AnimalView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The authoritative, persistent record of one ranch animal. It outlives any entity: the entity only
 * carries {@code animalId} and {@code generation}, and an entity whose generation is not the record's
 * is a stale copy. Mutate only on a world thread through {@link AnimalRegistry}, then call
 * {@link AnimalRegistry#markDirty}. Gson-serialised; field names are the save format.
 */
public final class AnimalRecord {
    public enum State {
        /** Has a live entity somewhere (maybe in an unloaded chunk). */
        ACTIVE,
        /** Asleep inside its house with no entity; the house spawns it again. */
        STORED,
        /** Entity lost; recovery will respawn it. */
        MISSING
    }

    public UUID animalId;
    /** Bumped every time a new entity is made for this animal. Only the entity with this generation is real. */
    public long generation;
    @Nullable public UUID entityUuid;
    @Nullable public UUID ownerId;
    public String speciesId = "";
    @Nullable public String name;
    public int hearts;
    /** Progress toward the next heart, 0 to 1. */
    public float heartProgress;
    public float happiness = 0.6f;
    @Nullable public UUID houseId;
    public boolean baby;
    public long bornAtMs;
    public long tamedAtMs;
    public boolean adopted;
    public List<String> traits = new ArrayList<>();
    @Nullable public String personality;
    @Nullable public UUID parentA;
    @Nullable public UUID parentB;
    public String worldName = "";
    public double lastX;
    public double lastY;
    public double lastZ;
    public State state = State.ACTIVE;
    public boolean following;
    public long lastBredAtMs;
    public long lastFedAtMs;
    public long lastPettedAtMs;
    /** Advanced feed in effect ({@code hearty}, {@code sweet}, {@code breeders}, {@code premium}), written by care. */
    @Nullable public String feedBonus;
    /** When {@link #feedBonus} wears off. */
    public long feedBonusUntilMs;
    /**
     * Role to spawn this animal with when it has no entity (a baby that inherits a colour, an animal stored
     * in its house). Null means "work it out from the species". Whoever swaps the live entity's role keeps
     * it current or clears it.
     */
    @Nullable public String spawnRole;

    @Nonnull
    public AnimalView view() {
        return new AnimalView(animalId, ownerId, speciesId, name, hearts, happiness, houseId, baby, List.copyOf(traits), worldName);
    }
}
