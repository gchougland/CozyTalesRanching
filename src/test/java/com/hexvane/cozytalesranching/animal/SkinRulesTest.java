package com.hexvane.cozytalesranching.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.animal.SkinRules.AssetView;
import com.hexvane.cozytalesranching.animal.SkinRules.Piece;
import com.hexvane.cozytalesranching.animal.SkinRules.Skin;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The skin rules on data shaped like Celly's Wildlife Skins (deer, moose) and vanilla's own random sets (cow, sheep). */
class SkinRulesTest {
    private static final String DEER = "NPC/Wildlife/Deer/Models/Model.blockymodel";
    private static final Piece EMPTY = new Piece(null, null, null, null);
    private static final Piece VANILLA_FLOWER = p("NPC/Wildlife/Deer/Models/Attachments/Head_Flower.blockymodel", "Antlers_Flower_Brown.png");
    private static final Piece BASE_2001 = p(DEER, "Attachments/Base_2001.png");
    private static final Piece BASE_2002 = p(DEER, "Attachments/Base_2002.png");
    private static final Piece EYES_2001 = p(DEER, "Attachments/Eyes_2001.png");
    private static final Piece FLORA_2003 = p("Attachments/Head_Flower_Normal.blockymodel", "Attachments/Flora_2003.png");

    private static Piece p(String model, String texture) {
        return new Piece(model, texture, null, null);
    }

    /** The skin pack's wild doe: no default flower, its flowers come from the Flora set. */
    private static AssetView skinnedDoe() {
        Map<String, Map<String, Piece>> sets = new LinkedHashMap<>();
        sets.put("Base", Map.of("2000", EMPTY, "2001", BASE_2001, "2002", BASE_2002));
        sets.put("Eyes", Map.of("2000", EMPTY, "2001", EYES_2001));
        sets.put("Flora", Map.of("0000", EMPTY, "2003", FLORA_2003));
        return new AssetView("Deer_Doe", DEER, List.of(), sets);
    }

    /** Vanilla's doe, as with no skin pack: the default flower, no random sets. */
    private static AssetView vanillaDoe() {
        return new AssetView("Deer_Doe", DEER, List.of(VANILLA_FLOWER), Map.of());
    }

    /** Our ranch deer: a copy of the vanilla doe on the same base model. */
    private static AssetView ranchDeer() {
        return new AssetView("CozyRanch_Deer", DEER, List.of(VANILLA_FLOWER), Map.of());
    }

    private static Map<String, String> ids(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void ranchDeerWearsTheWildSkinInsteadOfItsDefaultFlower() {
        SkinRules.Result r = SkinRules.resolve(ranchDeer(), skinnedDoe(), ids("Base", "2002", "Eyes", "2001", "Flora", "2003"));
        assertEquals(List.of(BASE_2002, EYES_2001, FLORA_2003), r.pieces());
        assertEquals(ids("Base", "2002", "Eyes", "2001", "Flora", "2003"), r.ids());
    }

    @Test
    void emptyChoicesDrawNothingButStayOnTheIds() {
        SkinRules.Result r = SkinRules.resolve(ranchDeer(), skinnedDoe(), ids("Base", "2000", "Eyes", "2001", "Flora", "0000"));
        assertEquals(List.of(EYES_2001), r.pieces());
        assertEquals(3, r.ids().size());
    }

    @Test
    void withTheSkinPackGoneTheRanchModelIsPlain() {
        SkinRules.Result r = SkinRules.resolve(ranchDeer(), vanillaDoe(), ids("Base", "2002", "Flora", "2003"));
        assertEquals(List.of(VANILLA_FLOWER), r.pieces());
        // the ids stay, so putting the pack back brings the same skin back
        assertEquals(ids("Base", "2002", "Flora", "2003"), r.ids());
    }

    @Test
    void noSourceMeansWhatTheEngineDraws() {
        SkinRules.Result r = SkinRules.resolve(ranchDeer(), null, ids("Base", "2002"));
        assertEquals(List.of(VANILLA_FLOWER), r.pieces());
    }

    @Test
    void aDifferentBaseModelNeverBorrows() {
        AssetView calf = new AssetView("CozyRanch_Deer_Other", "NPC/Wildlife/Fawn/Models/Model.blockymodel", List.of(), Map.of());
        SkinRules.Result r = SkinRules.resolve(calf, skinnedDoe(), ids("Base", "2002"));
        assertTrue(r.pieces().isEmpty());
        assertEquals(ids("Base", "2002"), r.ids());
    }

    @Test
    void baseModelsCompareByPathIgnoringCaseAndSlashes() {
        assertTrue(SkinRules.sameBase("NPC/Wildlife/Deer/Models/Model.blockymodel", "npc\\wildlife\\deer\\models\\model.blockymodel"));
        assertFalse(SkinRules.sameBase(DEER, null));
        assertFalse(SkinRules.sameBase(DEER, "NPC/Wildlife/Moose/Models/Model.blockymodel"));
    }

    @Test
    void theSameAssetResolvesNatively() {
        AssetView doe = skinnedDoe();
        SkinRules.Result r = SkinRules.resolve(doe, doe, ids("Base", "2001"));
        assertEquals(List.of(BASE_2001), r.pieces());
    }

    @Test
    void ownSetsResolveOnTheTargetAndBorrowedOnesAreAdded() {
        Piece udder = p("Cow/Udder.blockymodel", "Cow/Texture.png");
        Piece spots = p("Cow/Model.blockymodel", "Cow/Spots_2001.png");
        AssetView ranchCow = new AssetView("CozyRanch_Cow", "Cow/Model.blockymodel", List.of(), Map.of("Fleece", Map.of("Wool", udder)));
        AssetView skinnedCow = new AssetView("Cow", "Cow/Model.blockymodel", List.of(),
            Map.of("Fleece", Map.of("Wool", udder), "Base", Map.of("2001", spots)));
        SkinRules.Result r = SkinRules.resolve(ranchCow, skinnedCow, ids("Fleece", "Wool", "Base", "2001"));
        assertEquals(List.of(udder, spots), r.pieces());
    }

    @Test
    void freshTakesTheRecordOverTheRoll() {
        Map<String, String> live = ids("Fleece", "Wool", "Base", "2000");
        Map<String, String> record = ids("Base", "2003", "Eyes", "2001");
        assertEquals(ids("Fleece", "Wool", "Base", "2003", "Eyes", "2001"), SkinRules.mergeIds(live, record, Set.of("Fleece", "Base"), true));
    }

    @Test
    void inPlaceChangesWinForOwnSetsSoShearingSticks() {
        // a sheared ranch sheep: the role removed Fleece from its model; the record still says Wool
        Map<String, String> live = ids();
        Map<String, String> record = ids("Fleece", "Wool", "Base", "2002");
        Map<String, String> merged = SkinRules.mergeIds(live, record, Set.of("Fleece"), false);
        assertEquals(ids("Base", "2002"), merged);
    }

    @Test
    void aLoadedModelGetsTheBorrowedSetsBack() {
        Map<String, String> live = ids("Base", "2002");
        Map<String, String> record = ids("Base", "2002", "Flora", "2003");
        assertEquals(ids("Base", "2002", "Flora", "2003"), SkinRules.mergeIds(live, record, Set.of(), false));
    }

    @Test
    void mergeIgnoresBlankEntries() {
        Map<String, String> record = new LinkedHashMap<>();
        record.put("Base", "");
        record.put("", "2001");
        record.put("Eyes", null);
        assertTrue(SkinRules.mergeIds(null, record, Set.of(), true).isEmpty());
    }

    @Test
    void aBabyTakesAfterTheParentWithItsLook() {
        Skin doe = new Skin("Deer_Doe", ids("Base", "2001", "Flora", "2003"));
        Skin stag = new Skin("Model_Deer_Stag", ids("Base", "2002", "Antlers", "6001"));
        Skin fawn = SkinRules.inherit(doe, "CozyRanch_Deer", stag, "CozyRanch_Deer_Stag", "CozyRanch_Deer_Stag", new Random(1));
        assertNotNull(fawn);
        assertEquals("Model_Deer_Stag", fawn.source());
        assertEquals(stag.ids(), fawn.ids());
    }

    @Test
    void twoMatchingParentsMixSetBySet() {
        Skin a = new Skin("Deer_Doe", ids("Base", "2001", "Eyes", "2001", "Flora", "2003"));
        Skin b = new Skin("Deer_Doe", ids("Base", "2002", "Eyes", "2002"));
        Random rng = new Random(7);
        boolean sawA = false;
        boolean sawB = false;
        for (int i = 0; i < 50; i++) {
            Skin baby = SkinRules.inherit(a, "CozyRanch_Deer", b, "CozyRanch_Deer", "CozyRanch_Deer", rng);
            assertNotNull(baby);
            assertEquals("Deer_Doe", baby.source());
            assertEquals(Set.of("Base", "Eyes", "Flora"), baby.ids().keySet());
            assertEquals("2003", baby.ids().get("Flora"));
            sawA |= baby.ids().get("Base").equals("2001");
            sawB |= baby.ids().get("Base").equals("2002");
        }
        assertTrue(sawA && sawB);
    }

    @Test
    void noMatchingParentMeansNoInheritedSkin() {
        Skin doe = new Skin("Deer_Doe", ids("Base", "2001"));
        assertNull(SkinRules.inherit(doe, "CozyRanch_Deer", null, "CozyRanch_Deer", "CozyRanch_Deer_Stag", new Random(1)));
        assertNull(SkinRules.inherit(null, null, null, null, "CozyRanch_Deer", new Random(1)));
    }

    @Test
    void aParentWithAnUnknownRoleStillCounts() {
        Skin doe = new Skin("Deer_Doe", ids("Base", "2001"));
        Skin baby = SkinRules.inherit(doe, null, null, null, "CozyRanch_Deer", new Random(1));
        assertNotNull(baby);
        assertEquals(doe.ids(), baby.ids());
    }

    @Test
    void ofNeedsASourceAndIds() {
        assertNull(SkinRules.of(null, ids("Base", "2001")));
        assertNull(SkinRules.of("Deer_Doe", ids()));
        assertNotNull(SkinRules.of("Deer_Doe", ids("Base", "2001")));
    }

    @Test
    void theSkinIsSavedAndCopiedWithTheRecord() {
        AnimalRecord r = new AnimalRecord();
        r.animalId = UUID.randomUUID();
        r.skinModel = "Deer_Doe";
        r.skinIds = ids("Base", "2001", "Flora", "2003");
        AnimalRecord copy = RecordFields.copy(r);
        assertEquals(r.skinIds, copy.skinIds);
        assertFalse(r.skinIds == copy.skinIds, "a copy gets its own map");
        assertTrue(RecordFields.sameExceptPosition(r, copy));
        copy.skinIds = ids("Base", "2002");
        assertFalse(RecordFields.sameExceptPosition(r, copy));

        String json = AnimalSaveFile.GSON.toJson(r);
        AnimalRecord back = AnimalSaveFile.GSON.fromJson(json, AnimalRecord.class);
        assertEquals("Deer_Doe", back.skinModel);
        assertEquals(r.skinIds, back.skinIds);
        AnimalRecord old = AnimalSaveFile.GSON.fromJson("{\"animalId\":\"" + r.animalId + "\"}", AnimalRecord.class);
        assertNull(old.skinModel);
        assertNull(old.skinIds);
    }
}
