package com.hexvane.cozytalesranching.rehome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.rehome.RehomeRules.Offer;
import com.hexvane.cozytalesranching.rehome.RehomeRules.Verdict;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RehomeRulesTest {
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID cow = UUID.randomUUID();

    @Test
    void onlyTheOwnerWithTransferMayOffer() {
        assertEquals(Verdict.OK, RehomeRules.checkGive(alice, alice, bob, true, false, false));
        assertEquals(Verdict.NO_PERMISSION, RehomeRules.checkGive(alice, alice, bob, false, false, false));
        assertEquals(Verdict.NOT_OWNER, RehomeRules.checkGive(bob, alice, admin, true, false, false));
        assertEquals(Verdict.UNOWNED, RehomeRules.checkGive(alice, null, bob, true, false, false));
        assertEquals(Verdict.SELF, RehomeRules.checkGive(alice, alice, alice, true, false, false));
    }

    @Test
    void manageAnyAloneDoesNotMakeAnOfferButForcesATransfer() {
        assertEquals(Verdict.NOT_OWNER, RehomeRules.checkGive(admin, alice, bob, true, true, false));
        assertEquals(Verdict.OK, RehomeRules.checkGive(admin, alice, bob, false, true, true));
        assertEquals(Verdict.NO_PERMISSION, RehomeRules.checkGive(alice, alice, bob, true, false, true), "force needs manageany");
        assertEquals(Verdict.SELF, RehomeRules.checkGive(admin, alice, alice, false, true, true));
        assertEquals(Verdict.UNOWNED, RehomeRules.checkGive(admin, null, bob, false, true, true));
    }

    @Test
    void acceptNeedsPermissionRoomAndAnUnchangedOwner() {
        Offer o = new Offer(cow, alice, bob, 0);
        assertEquals(Verdict.OK, RehomeRules.checkAccept(o, alice, true, 3, 64));
        assertEquals(Verdict.NO_PERMISSION, RehomeRules.checkAccept(o, alice, false, 3, 64));
        assertEquals(Verdict.OWNER_CHANGED, RehomeRules.checkAccept(o, admin, true, 3, 64));
        assertEquals(Verdict.OWNER_CHANGED, RehomeRules.checkAccept(o, null, true, 3, 64), "released meanwhile");
        assertEquals(Verdict.RECIPIENT_FULL, RehomeRules.checkAccept(o, alice, true, 64, 64));
    }

    @Test
    void butcheringIsForTheOwnerOnlyAndNeverABaby() {
        assertEquals(Verdict.OK, RehomeRules.checkButcher(alice, alice, false, true));
        assertEquals(Verdict.NO_PERMISSION, RehomeRules.checkButcher(alice, alice, false, false));
        assertEquals(Verdict.NOT_OWNER, RehomeRules.checkButcher(admin, alice, false, true), "not even an admin");
        assertEquals(Verdict.UNOWNED, RehomeRules.checkButcher(alice, null, false, true));
        assertEquals(Verdict.BABY, RehomeRules.checkButcher(alice, alice, true, true));
    }

    @Test
    void offersExpireAfterAMinute() {
        Offer o = new Offer(cow, alice, bob, 10_000);
        assertFalse(RehomeRules.isExpired(o, 10_000));
        assertFalse(RehomeRules.isExpired(o, 10_000 + RehomeRules.OFFER_TTL_MS - 1));
        assertTrue(RehomeRules.isExpired(o, 10_000 + RehomeRules.OFFER_TTL_MS));
        assertTrue(RehomeRules.isExpired(o, 9_000), "a clock that went back");
    }

    @Test
    void theBookHandsOutAnOfferOnceAndDropsExpiredOnes() {
        OfferBook book = new OfferBook();
        book.put(new Offer(cow, alice, bob, 0));
        assertTrue(book.take(alice, 1).isEmpty(), "offers are found by recipient");
        assertEquals(cow, book.take(bob, 1).orElseThrow().animalId());
        assertTrue(book.take(bob, 2).isEmpty(), "taken once");

        book.put(new Offer(cow, alice, bob, 0));
        assertTrue(book.take(bob, RehomeRules.OFFER_TTL_MS).isEmpty(), "expired");

        book.put(new Offer(cow, alice, bob, 0));
        List<Offer> gone = book.prune(RehomeRules.OFFER_TTL_MS + 5);
        assertEquals(1, gone.size());
        assertEquals(0, book.size());
    }

    @Test
    void oneOfferPerRecipientAndPerAnimal() {
        OfferBook book = new OfferBook();
        UUID pig = UUID.randomUUID();
        assertNull(book.put(new Offer(cow, alice, bob, 0)));
        assertEquals(cow, book.put(new Offer(pig, alice, bob, 1)).animalId(), "a newer offer replaces the older");
        book.put(new Offer(pig, alice, admin, 2));
        assertTrue(book.peek(bob, 3).isEmpty(), "offering the pig to someone else moves the offer");
        assertEquals(pig, book.peek(admin, 3).orElseThrow().animalId());
    }

    @Test
    void namesBeforeIdPrefixes() {
        AnimalRecord daisy = record("Daisy");
        AnimalRecord other = record(null);
        assertEquals(List.of(daisy), RehomeAnimals.match("daisy", List.of(daisy, other)));
        String prefix = other.animalId.toString().substring(0, 6);
        assertEquals(List.of(other), RehomeAnimals.match(prefix, List.of(daisy, other)));
        assertTrue(RehomeAnimals.match("abc", List.of(daisy, other)).isEmpty(), "prefixes need four characters");
    }

    @Test
    void hostileMobProtectionDefaultsOn() {
        assertTrue(RehomeSettings.parseProtect(JsonParser.parseString("{}")));
        assertTrue(RehomeSettings.parseProtect(JsonParser.parseString("{\"ProtectFromHostileMobs\": \"no\"}")));
        assertFalse(RehomeSettings.parseProtect(JsonParser.parseString("{\"ProtectFromHostileMobs\": false}")));
        assertTrue(RehomeSettings.parseProtect(JsonParser.parseString("[]")));
    }

    private static AnimalRecord record(String name) {
        AnimalRecord r = new AnimalRecord();
        r.animalId = UUID.randomUUID();
        r.name = name;
        r.speciesId = "cow";
        return r;
    }
}
