package com.hexvane.cozytalesranching.journal;

import com.hexvane.cozytalesranching.journal.PlayerJournal.Entry;
import com.hexvane.cozytalesranching.journal.PlayerJournal.Fact;
import com.hexvane.cozytalesranching.journal.PlayerJournal.Memory;
import com.hexvane.cozytalesranching.journal.PlayerJournal.MemoryKind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What each thing a player sees writes into their journal. Pure and idempotent: telling it the same thing twice
 * changes nothing the second time. One call per event; the caller holds the journal's lock.
 */
public final class JournalRules {
    private JournalRules() {}

    /** What one observation added. {@code newEntry}: the species was met for the first time. */
    public record Change(@Nonnull String speciesId, boolean newEntry, boolean newNotes, @Nonnull List<Memory> memories) {
        public boolean any() {
            return newEntry || newNotes || !memories.isEmpty();
        }

        @Nonnull
        static Change none(@Nonnull String speciesId) {
            return new Change(speciesId, false, false, List.of());
        }
    }

    /** A first feeding try: the basic entry and the diet. */
    @Nonnull
    public static Change tameAttempt(@Nonnull PlayerJournal j, @Nonnull String species, long now) {
        Edit e = new Edit(j, species, now);
        e.fact(Fact.SEEN).fact(Fact.DIET);
        return e.done();
    }

    /** The player tamed or was given this animal. */
    @Nonnull
    public static Change befriended(@Nonnull PlayerJournal j, @Nonnull String species, @Nullable String name, @Nonnull Collection<String> traits, long now) {
        Edit e = new Edit(j, species, now);
        e.fact(Fact.SEEN).fact(Fact.DIET).fact(Fact.CARE).traits(traits);
        if (!e.hasMemory(MemoryKind.FIRST_FRIEND)) e.memory(MemoryKind.FIRST_FRIEND, name, null);
        else if (!e.hasMemoryFor(MemoryKind.FIRST_FRIEND) && !e.hasMemoryFor(MemoryKind.NEW_FRIEND)) e.memory(MemoryKind.NEW_FRIEND, name, null);
        return e.done();
    }

    /** A house for the species: placed by the player ({@code built}) or one of their animals moved into one. */
    @Nonnull
    public static Change housed(@Nonnull PlayerJournal j, @Nonnull String species, boolean built, long now) {
        Edit e = new Edit(j, species, now);
        e.fact(Fact.SEEN).fact(Fact.HABITAT).fact(Fact.WILDLIFE);
        if (built && !e.hasMemory(MemoryKind.FIRST_HOUSE)) e.memory(MemoryKind.FIRST_HOUSE, null, null);
        return e.done();
    }

    /** The player watched an animal do a special behaviour. */
    @Nonnull
    public static Change behaviour(@Nonnull PlayerJournal j, @Nonnull String species, @Nonnull String behaviourId, @Nullable String name, long now) {
        Edit e = new Edit(j, species, now);
        e.fact(Fact.SEEN).behaviour(behaviourId);
        if (!e.hasMemory(MemoryKind.FIRST_BEHAVIOUR)) e.memory(MemoryKind.FIRST_BEHAVIOUR, name, behaviourId);
        return e.done();
    }

    /** One of the player's animals had a baby. */
    @Nonnull
    public static Change bred(@Nonnull PlayerJournal j, @Nonnull String species, @Nullable String parentName, @Nonnull Collection<String> traits, long now) {
        Edit e = new Edit(j, species, now);
        e.fact(Fact.SEEN).fact(Fact.BREEDING).traits(traits);
        if (!e.hasMemory(MemoryKind.FIRST_BREEDING)) e.memory(MemoryKind.FIRST_BREEDING, parentName, null);
        return e.done();
    }

    /** Traits seen on an animal the player owns. */
    @Nonnull
    public static Change traits(@Nonnull PlayerJournal j, @Nonnull String species, @Nonnull Collection<String> traits, long now) {
        if (traits.isEmpty()) return Change.none(species);
        Edit e = new Edit(j, species, now);
        e.fact(Fact.SEEN).traits(traits);
        return e.done();
    }

    /** One of the player's animals made something. */
    @Nonnull
    public static Change produced(@Nonnull PlayerJournal j, @Nonnull String species, @Nonnull String itemId, long now) {
        Edit e = new Edit(j, species, now);
        e.fact(Fact.SEEN).product(itemId);
        return e.done();
    }

    /** Friendship moved from {@code before} to {@code after} hearts. */
    @Nonnull
    public static Change hearts(@Nonnull PlayerJournal j, @Nonnull String species, @Nullable String name, int before, int after, long now) {
        if (after < 5 || before >= 5) return Change.none(species);
        Edit e = new Edit(j, species, now);
        if (!e.hasMemory(MemoryKind.FIRST_FIVE_HEARTS)) e.memory(MemoryKind.FIRST_FIVE_HEARTS, name, null);
        return e.done();
    }

    /** A wild animal came to visit the player's ranch. */
    @Nonnull
    public static Change visitor(@Nonnull PlayerJournal j, @Nonnull String species, boolean rare, long now) {
        Edit e = new Edit(j, species, now);
        e.fact(Fact.SEEN).fact(Fact.DIET).fact(Fact.WILDLIFE);
        if (!e.hasMemory(MemoryKind.FIRST_VISITOR)) e.memory(MemoryKind.FIRST_VISITOR, null, null);
        if (rare) {
            e.fact(Fact.RARE);
            if (!e.hasMemory(MemoryKind.FIRST_RARE)) e.memory(MemoryKind.FIRST_RARE, null, null);
        }
        return e.done();
    }

    /** Collects one observation's changes to one species entry. */
    private static final class Edit {
        private final PlayerJournal journal;
        private final String species;
        private final long now;
        private final List<Memory> memories = new ArrayList<>();
        private boolean newEntry;
        private boolean newNotes;

        Edit(@Nonnull PlayerJournal journal, @Nonnull String species, long now) {
            this.journal = journal;
            this.species = species;
            this.now = now;
        }

        @Nonnull
        private Entry entry() {
            return journal.Species.computeIfAbsent(species, id -> new Entry());
        }

        @Nonnull
        Edit fact(@Nonnull Fact fact) {
            Entry en = entry();
            if (!en.Facts.add(fact.id)) return this;
            if (fact == Fact.SEEN) {
                newEntry = true;
                if (en.FirstSeenAtMs == 0) en.FirstSeenAtMs = now;
            } else {
                newNotes = true;
            }
            return this;
        }

        @Nonnull
        Edit traits(@Nonnull Collection<String> traits) {
            for (String t : traits) {
                if (t != null && !t.isBlank() && entry().Traits.add(t)) newNotes = true;
            }
            return this;
        }

        void behaviour(@Nonnull String id) {
            if (!id.isBlank() && entry().Behaviours.add(id)) newNotes = true;
        }

        void product(@Nonnull String itemId) {
            if (!itemId.isBlank() && entry().Products.add(itemId)) newNotes = true;
        }

        boolean hasMemory(@Nonnull MemoryKind kind) {
            for (Memory m : journal.Memories) if (kind.id.equals(m.Kind)) return true;
            return false;
        }

        boolean hasMemoryFor(@Nonnull MemoryKind kind) {
            for (Memory m : journal.Memories) if (kind.id.equals(m.Kind) && species.equals(m.Species)) return true;
            return false;
        }

        void memory(@Nonnull MemoryKind kind, @Nullable String name, @Nullable String detail) {
            Memory m = new Memory(kind, species, name == null || name.isBlank() ? null : name, detail, now);
            journal.Memories.add(m);
            memories.add(m);
        }

        @Nonnull
        Change done() {
            return new Change(species, newEntry, newNotes, List.copyOf(memories));
        }
    }
}
