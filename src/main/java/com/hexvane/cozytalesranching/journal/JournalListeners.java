package com.hexvane.cozytalesranching.journal;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.api.AnimalView;
import com.hexvane.cozytalesranching.api.event.AnimalBehaviourEvent;
import com.hexvane.cozytalesranching.api.event.AnimalBredEvent;
import com.hexvane.cozytalesranching.api.event.AnimalFriendshipChangedEvent;
import com.hexvane.cozytalesranching.api.event.AnimalGrewUpEvent;
import com.hexvane.cozytalesranching.api.event.AnimalHouseAssignedEvent;
import com.hexvane.cozytalesranching.api.event.AnimalOwnerChangedEvent;
import com.hexvane.cozytalesranching.api.event.AnimalProducedEvent;
import com.hexvane.cozytalesranching.api.event.AnimalTameAttemptEvent;
import com.hexvane.cozytalesranching.api.event.AnimalTamedEvent;
import com.hexvane.cozytalesranching.api.event.RanchHousePlacedEvent;
import com.hexvane.cozytalesranching.api.event.RanchVisitorArrivedEvent;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Turns the public ranch events into journal discoveries, then tells the player quietly. World threads. */
final class JournalListeners {
    private JournalListeners() {}

    static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        EventRegistry events = plugin.getEventRegistry();
        events.register(AnimalTameAttemptEvent.class, e ->
            observe(e.getPlayerId(), e.getSpeciesId(), j -> JournalRules.tameAttempt(j, e.getSpeciesId(), now())));
        events.register(AnimalTamedEvent.class, e -> {
            AnimalView a = e.getAnimal();
            UUID owner = e.getPlayerId() != null ? e.getPlayerId() : a.ownerId();
            observe(owner, a.speciesId(), j -> JournalRules.befriended(j, a.speciesId(), a.name(), a.traits(), now()));
        });
        events.register(AnimalOwnerChangedEvent.class, e -> {
            AnimalView a = e.getAnimal();
            observe(e.getNewOwnerId(), a.speciesId(), j -> JournalRules.befriended(j, a.speciesId(), a.name(), a.traits(), now()));
        });
        events.register(RanchHousePlacedEvent.class, e ->
            observe(e.getPlayerId(), e.getSpeciesId(), j -> JournalRules.housed(j, e.getSpeciesId(), true, now())));
        events.register(AnimalHouseAssignedEvent.class, e -> {
            AnimalView a = e.getAnimal();
            if (e.getNewHouseId() == null) return;
            observe(a.ownerId(), a.speciesId(), j -> JournalRules.housed(j, a.speciesId(), false, now()));
        });
        events.register(AnimalBehaviourEvent.class, e -> {
            AnimalView a = e.getAnimal();
            observe(e.getWitnessId(), a.speciesId(), j -> JournalRules.behaviour(j, a.speciesId(), e.getBehaviourId(), a.name(), now()));
        });
        events.register(AnimalBredEvent.class, e -> {
            AnimalView a = e.getAnimal();
            Set<String> traits = new LinkedHashSet<>();
            for (AnimalView baby : e.getOffspring()) traits.addAll(baby.traits());
            observe(a.ownerId(), a.speciesId(), j -> JournalRules.bred(j, a.speciesId(), a.name(), traits, now()));
        });
        events.register(AnimalGrewUpEvent.class, e -> {
            AnimalView a = e.getAnimal();
            observe(a.ownerId(), a.speciesId(), j -> JournalRules.traits(j, a.speciesId(), a.traits(), now()));
        });
        events.register(AnimalFriendshipChangedEvent.class, e -> {
            AnimalView a = e.getAnimal();
            observe(a.ownerId(), a.speciesId(), j -> JournalRules.hearts(j, a.speciesId(), a.name(), e.getPreviousHearts(), e.getNewHearts(), now()));
        });
        events.register(AnimalProducedEvent.class, e -> {
            AnimalView a = e.getAnimal();
            observe(a.ownerId(), a.speciesId(), j -> JournalRules.produced(j, a.speciesId(), e.getItemId(), now()));
        });
        events.register(RanchVisitorArrivedEvent.class, e ->
            observe(e.getRanchOwnerId(), e.getSpeciesId(), j -> JournalRules.visitor(j, e.getSpeciesId(), e.isRare(), now())));
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /** Applies one observation to one player's journal and tells them if anything was new. */
    static void observe(@Nullable UUID player, @Nonnull String speciesId, @Nonnull Function<PlayerJournal, JournalRules.Change> edit) {
        JournalService service = JournalService.get();
        if (player == null || service == null) return;
        SpeciesDefinition species = SpeciesRegistry.byId(speciesId).orElse(null);
        if (species == null) return;
        JournalRules.Change change = service.apply(player, edit);
        if (change.any()) tell(player, species, change);
    }

    private static void tell(@Nonnull UUID player, @Nonnull SpeciesDefinition species, @Nonnull JournalRules.Change change) {
        PlayerRef ref = Universe.get().getPlayer(player);
        if (ref == null) return;
        ref.sendMessage(ServerUiOut.message(notice(species, change)));
        SoundUtil.playSoundEvent2dToPlayer(ref, RanchSounds.JOURNAL_DISCOVERY, SoundCategory.UI);
    }

    /** One quiet line: a new memory first, else a new page, else new notes. */
    @Nonnull
    static Txt notice(@Nonnull SpeciesDefinition species, @Nonnull JournalRules.Change change) {
        if (!change.memories().isEmpty()) {
            String kind = change.memories().getFirst().Kind;
            return Txt.j("notify.memory").with("title", Txt.j("memory." + kind + ".title"));
        }
        return Txt.j(change.newEntry() ? "notify.entry" : "notify.notes").with("species", JournalView.speciesName(species));
    }
}
