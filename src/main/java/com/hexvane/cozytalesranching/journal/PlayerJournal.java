package com.hexvane.cozytalesranching.journal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** What one player has found out, saved as {@code Journal/<uuid>.json}. Field names are the save format. */
public final class PlayerJournal {
    public static final int FORMAT_VERSION = 1;

    public int FormatVersion = FORMAT_VERSION;
    public String Player = "";
    public long SavedAtMs;
    /** By species id. A species is in the map once anything about it is known. */
    public Map<String, Entry> Species = new LinkedHashMap<>();
    /** Oldest first. */
    public List<Memory> Memories = new ArrayList<>();

    /** What is known about one species. */
    public static final class Entry {
        public long FirstSeenAtMs;
        /** {@link Fact} ids. */
        public Set<String> Facts = new LinkedHashSet<>();
        public Set<String> Behaviours = new LinkedHashSet<>();
        public Set<String> Traits = new LinkedHashSet<>();
        public Set<String> Products = new LinkedHashSet<>();

        public boolean knows(@Nonnull Fact fact) {
            return Facts.contains(fact.id);
        }

        @Nonnull
        Entry copy() {
            Entry e = new Entry();
            e.FirstSeenAtMs = FirstSeenAtMs;
            e.Facts = new LinkedHashSet<>(Facts);
            e.Behaviours = new LinkedHashSet<>(Behaviours);
            e.Traits = new LinkedHashSet<>(Traits);
            e.Products = new LinkedHashSet<>(Products);
            return e;
        }
    }

    /** A moment the player had, with the animal's name when there was one. */
    public static final class Memory {
        /** {@link MemoryKind} id. */
        public String Kind = "";
        public String Species = "";
        @Nullable public String Name;
        /** Kind-specific: the behaviour id for {@code first_behaviour}. */
        @Nullable public String Detail;
        public long AtMs;

        public Memory() {}

        public Memory(@Nonnull MemoryKind kind, @Nonnull String species, @Nullable String name, @Nullable String detail, long atMs) {
            Kind = kind.id;
            Species = species;
            Name = name;
            Detail = detail;
            AtMs = atMs;
        }

        @Nonnull
        Memory copy() {
            return new Memory(MemoryKind.byId(Kind), Species, Name, Detail, AtMs);
        }
    }

    /** One piece of a species entry, revealed by one kind of event. */
    public enum Fact {
        SEEN("seen"),
        DIET("diet"),
        CARE("care"),
        HABITAT("habitat"),
        WILDLIFE("wildlife"),
        BREEDING("breeding"),
        RARE("rare");

        public final String id;

        Fact(String id) {
            this.id = id;
        }
    }

    public enum MemoryKind {
        FIRST_FRIEND("first_friend"),
        NEW_FRIEND("new_friend"),
        FIRST_HOUSE("first_house"),
        FIRST_BREEDING("first_breeding"),
        FIRST_RARE("first_rare"),
        FIRST_FIVE_HEARTS("first_five_hearts"),
        FIRST_BEHAVIOUR("first_behaviour"),
        FIRST_VISITOR("first_visitor");

        public final String id;

        MemoryKind(String id) {
            this.id = id;
        }

        @Nonnull
        public static MemoryKind byId(@Nullable String id) {
            for (MemoryKind k : values()) if (k.id.equals(id)) return k;
            return FIRST_FRIEND;
        }

        static boolean known(@Nullable String id) {
            for (MemoryKind k : values()) if (k.id.equals(id)) return true;
            return false;
        }
    }

    @Nullable
    public Entry entry(@Nonnull String speciesId) {
        return Species.get(speciesId);
    }

    public boolean knows(@Nonnull String speciesId, @Nonnull Fact fact) {
        Entry e = Species.get(speciesId);
        return e != null && e.knows(fact);
    }

    @Nonnull
    PlayerJournal normalise() {
        if (Player == null) Player = "";
        if (Species == null) Species = new LinkedHashMap<>();
        if (Memories == null) Memories = new ArrayList<>();
        Species.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        for (Entry e : Species.values()) {
            if (e.Facts == null) e.Facts = new LinkedHashSet<>();
            if (e.Behaviours == null) e.Behaviours = new LinkedHashSet<>();
            if (e.Traits == null) e.Traits = new LinkedHashSet<>();
            if (e.Products == null) e.Products = new LinkedHashSet<>();
        }
        Memories.removeIf(m -> m == null || !MemoryKind.known(m.Kind) || m.Species == null);
        return this;
    }

    @Nonnull
    public PlayerJournal copy() {
        PlayerJournal j = new PlayerJournal();
        j.FormatVersion = FormatVersion;
        j.Player = Player;
        j.SavedAtMs = SavedAtMs;
        Species.forEach((id, e) -> j.Species.put(id, e.copy()));
        for (Memory m : Memories) j.Memories.add(m.copy());
        return j;
    }
}
