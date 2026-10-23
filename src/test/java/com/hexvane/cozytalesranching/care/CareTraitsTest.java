package com.hexvane.cozytalesranching.care;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.cozytalesranching.breeding.TraitBook;
import com.hexvane.cozytalesranching.taming.TamingRules.FoodKind;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The care-side traits as the bundled Traits.json defines them. */
class CareTraitsTest {
    private static final long MIN = 60_000L;
    private static final float EPS = 1e-4f;
    private static final float FLOOR = 0.45f;
    private static TraitBook book;

    @BeforeAll
    static void readRealTraits() throws Exception {
        try (InputStream in = CareTraitsTest.class.getResourceAsStream("/Server/CozyTalesRanching/Traits.json")) {
            assertNotNull(in);
            book = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), TraitBook.class).normalise();
        }
    }

    private static CareTraits of(String... traits) {
        return CareTraits.of(book, List.of(traits), FLOOR);
    }

    @Test
    void aPlainAnimalHasNoEffects() {
        assertEquals(CareTraits.NONE, of());
        assertEquals(CareTraits.NONE.floor(), of("bountiful", "lucky", "unknown").floor(), EPS);
        assertEquals(1.0, of("bountiful").foodUse(), 1e-9);
    }

    @Test
    void hardyDeclinesSlowerAndHasAFloor() {
        CareTraits hardy = of("hardy");
        assertEquals(0.5, hardy.declineMultiplier(), 1e-9);
        assertEquals(FLOOR, hardy.floor(), EPS);
        float plain = CareRules.drift(0.9f, 0.3f, 10 * MIN);
        float tough = CareRules.drift(0.9f, 0.3f, 10 * MIN, hardy.declineMultiplier(), hardy.floor());
        assertTrue(tough > plain, "falls more slowly");
        assertEquals(0.9f + (0.45f - 0.9f) * (1 - Math.exp(-10.0 / 40.0)), tough, EPS);
        float low = 0.5f;
        for (int i = 0; i < 100; i++) low = CareRules.drift(low, 0.3f, 10 * MIN, hardy.declineMultiplier(), hardy.floor());
        assertEquals(FLOOR, low, EPS, "never below its floor");
        assertEquals(CareRules.drift(0.4f, 1f, 5 * MIN), CareRules.drift(0.4f, 1f, 5 * MIN, 0.5, FLOOR), EPS, "rising is unchanged");
    }

    @Test
    void anOwnHappinessFloorWins() {
        TraitBook.Trait t = new TraitBook.Trait();
        t.Id = "sturdy";
        t.Effects = Map.of(CareTraits.HAPPINESS_FLOOR, 0.6);
        TraitBook b = new TraitBook();
        b.Traits = List.of(t);
        assertEquals(0.6f, CareTraits.of(b.normalise(), List.of("sturdy"), FLOOR).floor(), EPS);
    }

    @Test
    void gourmetGetsABiggerTreat() {
        CareTraits gourmet = of("gourmet");
        assertEquals(1.5, gourmet.treatMultiplier(), 1e-9);
        CareRules.Inputs in = new CareRules.Inputs(true, FoodKind.FAVORITE, true, false, false, 0f, false, false, gourmet.treatMultiplier());
        assertEquals(CareRules.TREAT * 1.5f, CareRules.target(in).treat(), EPS);
        assertEquals(CareRules.TROUGH_TREAT_FRIENDSHIP * 1.5f, (float) (CareRules.TROUGH_TREAT_FRIENDSHIP * gourmet.treatMultiplier()), EPS);
    }

    @Test
    void efficientEaterUsesUpFewerItems() {
        CareTraits eater = of("efficient_eater");
        assertEquals(0.75, eater.foodUse(), 1e-9);
        assertTrue(CareRules.usesItem(eater.foodUse(), 0.74));
        assertFalse(CareRules.usesItem(eater.foodUse(), 0.75));
        assertTrue(CareRules.usesItem(1.0, 0.999), "a plain animal always uses its item");
        SplittableRandom rng = new SplittableRandom(7);
        int used = 0;
        for (int i = 0; i < 10_000; i++) if (CareRules.usesItem(eater.foodUse(), rng.nextDouble())) used++;
        assertEquals(7500, used, 200);
    }

    @Test
    void affectionateGainsMoreFriendship() {
        CareTraits warm = of("affectionate");
        assertEquals(1.25, warm.friendship(), 1e-9);
        assertEquals(1.25f, CareRules.friendshipMultiplier(warm.friendship(), false, 1.5f), EPS);
        assertEquals(1.25 * 1.5, of("affectionate", "gourmet").friendship() * of("affectionate", "gourmet").treatMultiplier(), 1e-9);
    }
}
