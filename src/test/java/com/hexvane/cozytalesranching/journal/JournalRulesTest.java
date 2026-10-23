package com.hexvane.cozytalesranching.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.journal.PlayerJournal.Fact;
import com.hexvane.cozytalesranching.journal.PlayerJournal.MemoryKind;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalRulesTest {
    static final UUID ALICE = UUID.fromString("11111111-2222-4333-8444-555555555555");
    static final UUID BOB = UUID.fromString("66666666-7777-4888-9999-000000000000");

    @Test
    void firstFeedingTryRevealsTheEntryAndTheDietOnly() {
        PlayerJournal j = new PlayerJournal();
        JournalRules.Change c = JournalRules.tameAttempt(j, "cow", 1000);
        assertTrue(c.newEntry());
        assertTrue(j.knows("cow", Fact.SEEN));
        assertTrue(j.knows("cow", Fact.DIET));
        assertFalse(j.knows("cow", Fact.CARE));
        assertFalse(j.knows("cow", Fact.HABITAT));
        assertEquals(1000, j.entry("cow").FirstSeenAtMs);
        assertTrue(j.Memories.isEmpty());
    }

    @Test
    void everyRuleIsIdempotent() {
        PlayerJournal j = new PlayerJournal();
        List<Runnable> twice = List.of(
            () -> JournalRules.tameAttempt(j, "pig", 1),
            () -> JournalRules.befriended(j, "pig", "Truffles", List.of("hardy"), 2),
            () -> JournalRules.housed(j, "pig", true, 3),
            () -> JournalRules.behaviour(j, "pig", "mud_roll", "Truffles", 4),
            () -> JournalRules.bred(j, "pig", "Truffles", List.of("lucky"), 5),
            () -> JournalRules.produced(j, "pig", "CozyRanch_Truffle", 6),
            () -> JournalRules.hearts(j, "pig", "Truffles", 4, 5, 7),
            () -> JournalRules.visitor(j, "pig", true, 8));
        for (Runnable r : twice) r.run();
        String once = JournalSaveFile.serialize(j);
        assertFalse(JournalRules.tameAttempt(j, "pig", 11).any());
        assertFalse(JournalRules.befriended(j, "pig", "Truffles", List.of("hardy"), 12).any());
        assertFalse(JournalRules.housed(j, "pig", true, 13).any());
        assertFalse(JournalRules.behaviour(j, "pig", "mud_roll", "Truffles", 14).any());
        assertFalse(JournalRules.bred(j, "pig", "Truffles", List.of("lucky"), 15).any());
        assertFalse(JournalRules.produced(j, "pig", "CozyRanch_Truffle", 16).any());
        assertFalse(JournalRules.hearts(j, "pig", "Truffles", 4, 5, 17).any());
        assertFalse(JournalRules.visitor(j, "pig", true, 18).any());
        assertEquals(once, JournalSaveFile.serialize(j));
    }

    @Test
    void eachEventRevealsItsOwnPart() {
        PlayerJournal j = new PlayerJournal();
        JournalRules.befriended(j, "sheep", "Woolsworth", List.of("affectionate"), 1);
        assertTrue(j.knows("sheep", Fact.CARE));
        assertEquals(List.of("affectionate"), List.copyOf(j.entry("sheep").Traits));
        assertFalse(j.knows("sheep", Fact.HABITAT));

        JournalRules.housed(j, "sheep", false, 2);
        assertTrue(j.knows("sheep", Fact.HABITAT));
        assertTrue(j.knows("sheep", Fact.WILDLIFE));
        assertFalse(hasMemory(j, MemoryKind.FIRST_HOUSE), "moving into a house someone else built is not building one");

        assertTrue(JournalRules.behaviour(j, "sheep", "pronk", null, 3).newNotes());
        assertEquals(List.of("pronk"), List.copyOf(j.entry("sheep").Behaviours));
        assertFalse(j.knows("sheep", Fact.BREEDING));

        JournalRules.bred(j, "sheep", "Woolsworth", List.of("lucky"), 4);
        assertTrue(j.knows("sheep", Fact.BREEDING));
        assertTrue(j.entry("sheep").Traits.contains("lucky"));

        JournalRules.produced(j, "sheep", "Ingredient_Fabric_Scrap_Wool", 5);
        assertEquals(List.of("Ingredient_Fabric_Scrap_Wool"), List.copyOf(j.entry("sheep").Products));
        assertFalse(j.knows("sheep", Fact.RARE));

        JournalRules.visitor(j, "sheep", false, 6);
        assertFalse(j.knows("sheep", Fact.RARE));
        assertFalse(hasMemory(j, MemoryKind.FIRST_RARE));
        JournalRules.visitor(j, "sheep", true, 7);
        assertTrue(j.knows("sheep", Fact.RARE));
        assertTrue(hasMemory(j, MemoryKind.FIRST_RARE));
    }

    @Test
    void aVisitorIsAnEntryWithDietAndVisitorsButNoCare() {
        PlayerJournal j = new PlayerJournal();
        JournalRules.Change c = JournalRules.visitor(j, "frog", false, 9);
        assertTrue(c.newEntry());
        assertTrue(j.knows("frog", Fact.DIET));
        assertTrue(j.knows("frog", Fact.WILDLIFE));
        assertFalse(j.knows("frog", Fact.CARE));
        assertEquals(MemoryKind.FIRST_VISITOR.id, c.memories().getFirst().Kind);
    }

    @Test
    void firstFriendThenOneNewFriendMemoryPerSpecies() {
        PlayerJournal j = new PlayerJournal();
        JournalRules.befriended(j, "cow", "Clover", List.of(), 1);
        JournalRules.befriended(j, "cow", "Daisy", List.of(), 2);
        JournalRules.befriended(j, "pig", null, List.of(), 3);
        JournalRules.befriended(j, "pig", "Truffles", List.of(), 4);
        assertEquals(2, j.Memories.size());
        assertEquals(MemoryKind.FIRST_FRIEND.id, j.Memories.get(0).Kind);
        assertEquals("Clover", j.Memories.get(0).Name);
        assertEquals(MemoryKind.NEW_FRIEND.id, j.Memories.get(1).Kind);
        assertNull(j.Memories.get(1).Name);
        assertEquals(3, j.Memories.get(1).AtMs);
    }

    @Test
    void fiveHeartsOnlyWhenCrossingFive() {
        PlayerJournal j = new PlayerJournal();
        assertFalse(JournalRules.hearts(j, "cow", "Clover", 3, 4, 1).any());
        assertFalse(JournalRules.hearts(j, "cow", "Clover", 5, 5, 2).any());
        assertTrue(JournalRules.hearts(j, "cow", "Clover", 4, 5, 3).any());
        assertFalse(JournalRules.hearts(j, "chicken", "Pip", 4, 5, 4).any(), "only the first five-heart animal is a memory");
    }

    @Test
    void firstBehaviourMemoryKeepsTheBehaviour() {
        PlayerJournal j = new PlayerJournal();
        JournalRules.behaviour(j, "pig", "mud_roll", "Truffles", 1);
        JournalRules.behaviour(j, "pig", "snuffle", "Truffles", 2);
        assertEquals(1, j.Memories.size());
        assertEquals("mud_roll", j.Memories.getFirst().Detail);
        assertEquals(2, j.entry("pig").Behaviours.size());
    }

    @Test
    void journalsArePerPlayer(@TempDir Path dir) {
        JournalStore store = new JournalStore(dir);
        store.apply(ALICE, j -> JournalRules.befriended(j, "cow", "Clover", List.of(), 1));
        assertTrue(store.copy(ALICE).knows("cow", Fact.CARE));
        assertFalse(store.copy(BOB).knows("cow", Fact.SEEN));
        assertTrue(store.copy(BOB).Memories.isEmpty());
    }

    @Test
    void savedJournalsLoadBack(@TempDir Path dir) {
        JournalStore store = new JournalStore(dir);
        store.apply(ALICE, j -> JournalRules.befriended(j, "cow", "Clover", List.of("hardy"), 1));
        store.apply(ALICE, j -> JournalRules.behaviour(j, "cow", "graze", "Clover", 2));
        assertTrue(store.hasDirty());
        assertEquals(0, store.saveDirty());
        assertFalse(store.hasDirty());

        JournalStore again = new JournalStore(dir);
        again.loadAll();
        PlayerJournal j = again.copy(ALICE);
        assertTrue(j.knows("cow", Fact.CARE));
        assertTrue(j.entry("cow").Behaviours.contains("graze"));
        assertEquals(2, j.Memories.size());
        assertEquals(JournalSaveFile.serialize(store.copy(ALICE)).replaceAll("\"SavedAtMs\": \\d+", ""),
            JournalSaveFile.serialize(j).replaceAll("\"SavedAtMs\": \\d+", ""));
    }

    @Test
    void nothingNewIsNotSaved(@TempDir Path dir) {
        JournalStore store = new JournalStore(dir);
        store.apply(ALICE, j -> JournalRules.tameAttempt(j, "cow", 1));
        store.saveDirty();
        store.apply(ALICE, j -> JournalRules.tameAttempt(j, "cow", 2));
        assertFalse(store.hasDirty());
    }

    @Test
    void aBrokenFileFallsBackToThePreviousCopy(@TempDir Path dir) throws Exception {
        JournalStore store = new JournalStore(dir);
        store.apply(ALICE, j -> JournalRules.tameAttempt(j, "cow", 1));
        store.saveDirty();
        store.apply(ALICE, j -> JournalRules.tameAttempt(j, "pig", 2));
        store.saveDirty();
        java.nio.file.Files.writeString(dir.resolve(JournalSaveFile.fileNameFor(ALICE)), "{ not json");
        JournalStore again = new JournalStore(dir);
        again.loadAll();
        PlayerJournal j = again.copy(ALICE);
        assertTrue(j.knows("cow", Fact.SEEN));
        assertFalse(j.knows("pig", Fact.SEEN));
    }

    private static boolean hasMemory(PlayerJournal j, MemoryKind kind) {
        return j.Memories.stream().anyMatch(m -> kind.id.equals(m.Kind));
    }
}
