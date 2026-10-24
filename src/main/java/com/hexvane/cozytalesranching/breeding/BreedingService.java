package com.hexvane.cozytalesranching.breeding;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.api.AnimalView;
import com.hexvane.cozytalesranching.api.event.AnimalBredEvent;
import com.hexvane.cozytalesranching.api.event.AnimalFriendshipChangedEvent;
import com.hexvane.cozytalesranching.api.event.AnimalGrewUpEvent;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.house.HouseDayHooks;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import com.hexvane.cozytalesranching.taming.TamingApi;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hexvane.cozytalesranching.behaviour.AnimalClips;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Breeding at the house's morning, the "they bred" signal when the family comes out, and growing up. Everything runs
 * on the world thread outside system ticks: the morning hook is called by the house, the passes are queued with
 * {@code world.execute} by {@link GrowthSystem}.
 */
final class BreedingService implements HouseDayHooks.Hook {
    static final String LOVE_ANIMATION = "Love";
    /** Two plays of the 1.5 s clip. */
    static final double LOVE_SECONDS = 3.0;
    /** The family comes out right after the hooks; look for their entities for this long. */
    private static final long SIGNAL_WINDOW_MS = 20_000L;
    static final int GROWTH_EVERY_PASSES = 5;
    static final int REINDEX_EVERY_PASSES = 60;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private record Signal(@Nonnull String world, @Nonnull List<UUID> parents, @Nonnull List<UUID> babies, long untilMs, @Nonnull Set<UUID> done) {}

    enum GrowResult { GREW, NOT_BABY, NO_SPECIES, NOT_LOADED, SWAP_PENDING, NOT_RANCH_ROLE }

    private final BabyIndex babies = new BabyIndex();
    private final ConcurrentLinkedQueue<Signal> signals = new ConcurrentLinkedQueue<>();

    @Override
    public void onMorning(@Nonnull World world, @Nonnull HouseRecord house, @Nonnull List<AnimalRecord> residents) {
        runMorning(world, house, residents);
    }

    /** Decides and, when the rules allow, breeds. Returns the decision so the debug command can say why. */
    @Nonnull
    BreedingRules.Plan runMorning(@Nonnull World world, @Nonnull HouseRecord house, @Nonnull List<AnimalRecord> residents) {
        RanchConfig config = RanchConfig.get();
        SpeciesDefinition species = SpeciesRegistry.byId(house.speciesId).orElse(null);
        boolean mayBreed = house.ownerId != null && RanchPermissions.has(house.ownerId, RanchPermissions.BREED);
        BreedingRules.Settings settings = new BreedingRules.Settings(config.breedingEnabled(), config.breedingMinHearts(), config.breedingMinHappiness());
        long now = System.currentTimeMillis();
        TraitBook book = TraitRegistry.get();
        BreedingRules.Plan plan = BreedingRules.plan(house, residents, species, book, settings, mayBreed, now, ThreadLocalRandom.current());
        if (plan.outcome() == BreedingRules.Outcome.BRED && species != null) breed(world, house, species, book, plan, now);
        else if (config.debugLogging()) LOGGER.atInfo().log("Breeding: house %s did not breed: %s", house.houseId, plan.outcome());
        return plan;
    }

    private void breed(@Nonnull World world, @Nonnull HouseRecord house, @Nonnull SpeciesDefinition species, @Nonnull TraitBook book,
                       @Nonnull BreedingRules.Plan plan, long now) {
        AnimalRecord a = plan.parentA();
        AnimalRecord b = plan.parentB();
        if (a == null || b == null) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<AnimalRecord> born = new ArrayList<>();
        for (int i = 0; i < plan.babies(); i++) {
            AnimalRecord baby = Ranch.animals().create(species.Id, house.ownerId, world.getName(), true);
            baby.houseId = house.houseId;
            baby.state = AnimalRecord.State.STORED;
            baby.bornAtMs = now;
            baby.parentA = a.animalId;
            baby.parentB = b.animalId;
            baby.traits = new ArrayList<>(BreedingRules.inheritTraits(a.traits, b.traits, book, rng));
            baby.hearts = BreedingRules.BABY_HEARTS;
            baby.spawnRole = BreedingRules.babyRole(species, roleOf(world, rng.nextBoolean() ? a : b));
            baby.lastX = house.doorX;
            baby.lastY = house.doorY;
            baby.lastZ = house.doorZ;
            Ranch.animals().markDirty(baby);
            babies.add(world.getName(), baby.animalId);
            born.add(baby);
        }
        a.lastBredAtMs = now;
        b.lastBredAtMs = now;
        Ranch.animals().markDirty(a);
        Ranch.animals().markDirty(b);

        List<AnimalView> offspring = born.stream().map(AnimalRecord::view).toList();
        RanchEvents.fire(new AnimalBredEvent(a.view(), b.view(), offspring, house.houseId));
        for (AnimalRecord baby : born) {
            RanchEvents.fire(new AnimalFriendshipChangedEvent(baby.view(), 0, baby.hearts));
        }
        signals.add(new Signal(world.getName(), List.of(a.animalId, b.animalId), born.stream().map(r -> r.animalId).toList(),
            now + SIGNAL_WINDOW_MS, new HashSet<>()));
        LOGGER.atInfo().log("Breeding: house %s (%s) bred %s + %s -> %d baby: %s", house.houseId, species.Id, a.animalId, b.animalId,
            born.size(), born.stream().map(r -> r.animalId + " " + r.spawnRole + " " + r.traits).toList());
    }

    /** The parent's role as stored by the house, else its live role, else null (the species default). */
    @Nullable
    private static String roleOf(@Nonnull World world, @Nonnull AnimalRecord parent) {
        if (parent.spawnRole != null && !parent.spawnRole.isEmpty()) return parent.spawnRole;
        if (parent.entityUuid == null) return null;
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(parent.entityUuid);
        if (ref == null || !ref.isValid()) return null;
        NPCEntity npc = world.getEntityStore().getStore().getComponent(ref, NPCEntity.getComponentType());
        return npc == null ? null : npc.getRoleName();
    }

    /** One pass a second per world: signals every pass, growth every few, a fresh baby index every minute. */
    void pass(@Nonnull World world, long passNumber) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (!signals.isEmpty()) playSignals(world, store);
        if (passNumber % REINDEX_EVERY_PASSES == 1) reindex(world);
        if (passNumber % GROWTH_EVERY_PASSES == 0) growBabies(world, store);
    }

    private void playSignals(@Nonnull World world, @Nonnull Store<EntityStore> store) {
        long now = System.currentTimeMillis();
        for (Signal s : signals) {
            if (!s.world().equals(world.getName())) continue;
            if (now > s.untilMs()) {
                signals.remove(s);
                continue;
            }
            for (UUID id : s.parents()) {
                if (s.done().contains(id)) continue;
                Ref<EntityStore> ref = liveEntity(world, id);
                if (ref != null) {
                    playLove(ref, store);
                    s.done().add(id);
                }
            }
            for (UUID id : s.babies()) {
                if (s.done().contains(id)) continue;
                Ref<EntityStore> ref = liveEntity(world, id);
                if (ref != null) {
                    TamingApi.cheer(ref, store);
                    Ranch.animals().get(id).ifPresent(r -> RanchSounds.play(RanchSounds.BABY_BORN, r.lastX, r.lastY + 0.5, r.lastZ, store));
                    s.done().add(id);
                }
            }
            if (s.done().size() == s.parents().size() + s.babies().size()) signals.remove(s);
        }
    }

    /** The animal's current entity when it is out of the house and loaded. */
    @Nullable
    private static Ref<EntityStore> liveEntity(@Nonnull World world, @Nonnull UUID animalId) {
        AnimalRecord r = Ranch.animals().get(animalId).orElse(null);
        if (r == null || r.state != AnimalRecord.State.ACTIVE || r.entityUuid == null) return null;
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(r.entityUuid);
        return ref != null && ref.isValid() ? ref : null;
    }

    private static void playLove(@Nonnull Ref<EntityStore> animal, @Nonnull Store<EntityStore> store) {
        AnimalClips.playFor(store, animal, LOVE_ANIMATION, LOVE_SECONDS);
    }

    /** Babies of the world's houses and of its online players. Cost: houses x residents + players x animals, once a minute. */
    void reindex(@Nonnull World world) {
        String name = world.getName();
        for (HouseRecord h : Ranch.houses().inWorld(name)) {
            for (AnimalRecord r : Ranch.animals().inHouse(h.houseId)) {
                if (r.baby && name.equals(r.worldName)) babies.add(name, r.animalId);
            }
        }
        for (PlayerRef p : world.getPlayerRefs()) {
            for (AnimalRecord r : Ranch.animals().ownedBy(p.getUuid())) {
                if (r.baby && name.equals(r.worldName)) babies.add(name, r.animalId);
            }
        }
    }

    private void growBabies(@Nonnull World world, @Nonnull Store<EntityStore> store) {
        String name = world.getName();
        long now = System.currentTimeMillis();
        TraitBook book = TraitRegistry.get();
        for (UUID id : babies.inWorld(name)) {
            AnimalRecord r = Ranch.animals().get(id).orElse(null);
            if (r == null || !r.baby || !name.equals(r.worldName)) {
                babies.remove(name, id);
                continue;
            }
            if (BreedingRules.growthStartMs(r) == 0) {
                r.bornAtMs = now;
                Ranch.animals().markDirty(r);
                continue;
            }
            SpeciesDefinition species = SpeciesRegistry.byId(r.speciesId).orElse(null);
            if (species == null || !BreedingRules.isGrown(r, species, book, now)) continue;
            growUp(world, r, store);
        }
    }

    /**
     * Makes a baby an adult now. A loaded baby swaps to the adult role; a stored or missing one only changes the role it
     * will spawn with. An ACTIVE baby whose entity is not loaded waits, so it never comes back with a baby role.
     */
    @Nonnull
    GrowResult growUp(@Nonnull World world, @Nonnull AnimalRecord r, @Nonnull Store<EntityStore> store) {
        if (!r.baby) {
            babies.remove(world.getName(), r.animalId);
            return GrowResult.NOT_BABY;
        }
        SpeciesDefinition species = SpeciesRegistry.byId(r.speciesId).orElse(null);
        if (species == null) return GrowResult.NO_SPECIES;
        String adult;
        if (r.state == AnimalRecord.State.ACTIVE) {
            if (r.entityUuid == null) return GrowResult.NOT_LOADED;
            Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(r.entityUuid);
            if (ref == null || !ref.isValid()) return GrowResult.NOT_LOADED;
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            String current = npc == null ? null : npc.getRoleName();
            boolean ours = current != null && SpeciesRegistry.byRole(current)
                .map(m -> m.species() == species && (m.kind() == RoleKind.RANCH || m.kind() == RoleKind.RANCH_BABY))
                .orElse(false);
            if (!ours) return GrowResult.NOT_RANCH_ROLE;
            adult = BreedingRules.adultRole(species, current);
            // A baby that is the adult model scaled down (rabbit, mouse, duck, ...) keeps its spawn scale through a role
            // swap, since the new appearance is built with initialModelScale: reset it to full size first.
            if (!adult.isEmpty() && npc != null) npc.setInitialModelScale(1f);
            if (adult.isEmpty() || !TamingApi.swapRole(ref, adult, null, store)) return GrowResult.SWAP_PENDING;
        } else {
            adult = BreedingRules.adultRole(species, r.spawnRole);
        }
        r.baby = false;
        r.spawnRole = adult.isEmpty() ? null : adult;
        Ranch.animals().markDirty(r);
        babies.remove(world.getName(), r.animalId);
        RanchEvents.fire(new AnimalGrewUpEvent(r.view()));
        LOGGER.atInfo().log("Breeding: %s (%s) grew up, role %s, %s", r.animalId, r.speciesId, adult, r.state);
        return GrowResult.GREW;
    }

    int babiesIndexed(@Nonnull String world) {
        return babies.size(world);
    }
}
