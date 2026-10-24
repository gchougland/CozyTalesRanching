package com.hexvane.cozytalesranching.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VariantProductsTest {
    private static SpeciesDefinition.Product product(String item, double minutes) {
        SpeciesDefinition.Product p = new SpeciesDefinition.Product();
        p.Item = item;
        p.IntervalMinutes = minutes;
        return p;
    }

    private static SpeciesDefinition snail() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "snail";
        s.RanchRole = "CozyRanch_Snail_Frost";
        s.RanchBabyRole = "CozyRanch_Snail_Frost_Baby";
        s.RanchRoleVariants = Map.of("Snail_Magma", "CozyRanch_Snail_Magma");
        s.RanchBabyRoleVariants = Map.of("CozyRanch_Snail_Frost", "CozyRanch_Snail_Frost_Baby", "CozyRanch_Snail_Magma", "CozyRanch_Snail_Magma_Baby");
        s.Products = List.of(product("Ingredient_Ice_Essence", 50));
        s.VariantProducts = Map.of("CozyRanch_Snail_Magma", List.of(product("Ingredient_Fire_Essence", 50)));
        return s.normalise();
    }

    @Test
    void aLookMakesItsOwnProducts() {
        SpeciesDefinition s = snail();
        assertEquals("Ingredient_Fire_Essence", s.productsFor("CozyRanch_Snail_Magma").getFirst().Item);
        assertEquals("Ingredient_Ice_Essence", s.productsFor("CozyRanch_Snail_Frost").getFirst().Item);
    }

    @Test
    void aBabyLookReadsItsAdultLook() {
        assertEquals("Ingredient_Fire_Essence", snail().productsFor("CozyRanch_Snail_Magma_Baby").getFirst().Item);
    }

    @Test
    void unknownOrMissingRolesGetTheSpeciesProducts() {
        SpeciesDefinition s = snail();
        assertSame(s.Products, s.productsFor(null));
        assertSame(s.Products, s.productsFor("Something_Else"));
    }

    @Test
    void allProductsListsEachItemOnceDefaultFirst() {
        List<String> items = snail().allProducts().stream().map(p -> p.Item).toList();
        assertEquals(List.of("Ingredient_Ice_Essence", "Ingredient_Fire_Essence"), items);
    }

    @Test
    void aSpeciesWithoutTheFieldIsUnchanged() {
        SpeciesDefinition cow = new SpeciesDefinition();
        cow.Id = "cow";
        cow.Products = List.of(product("Food_Milk", 30));
        cow.VariantProducts = null;
        cow.BirthVariants = null;
        cow.normalise();
        assertTrue(cow.VariantProducts.isEmpty());
        assertTrue(cow.BirthVariants.isEmpty());
        assertSame(cow.Products, cow.productsFor("CozyRanch_Cow"));
        assertSame(cow.Products, cow.allProducts());
    }

    @Test
    void birthRulesWithoutARoleOrConditionAreDropped() {
        SpeciesDefinition s = new SpeciesDefinition();
        SpeciesDefinition.BirthVariant noRole = new SpeciesDefinition.BirthVariant();
        noRole.Feature = "water";
        SpeciesDefinition.BirthVariant noCondition = new SpeciesDefinition.BirthVariant();
        noCondition.Role = "X";
        SpeciesDefinition.BirthVariant ok = new SpeciesDefinition.BirthVariant();
        ok.Role = "Y";
        ok.Feature = "water";
        ok.Parents = null;
        s.BirthVariants = java.util.Arrays.asList(noRole, null, noCondition, ok);
        s.normalise();
        assertEquals(1, s.BirthVariants.size());
        assertEquals("Y", s.BirthVariants.getFirst().Role);
        assertTrue(s.BirthVariants.getFirst().Parents.isEmpty());
    }

    /** The bundled snail file: six shells, each with one essence, and every look but the default has its own list. */
    @Test
    void bundledSnailHasAnEssencePerShell() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/Server/CozyTalesRanching/Species/Snail.json")) {
            assertNotNull(in);
            SpeciesDefinition s = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), SpeciesDefinition.class).normalise();
            Map<String, String> want = Map.of(
                "CozyRanch_Snail_Frost", "Ingredient_Ice_Essence",
                "CozyRanch_Snail_Magma", "Ingredient_Fire_Essence",
                "CozyRanch_Snail_Garden", "Ingredient_Life_Essence",
                "CozyRanch_Snail_Tide", "Ingredient_Water_Essence",
                "CozyRanch_Snail_Storm", "Ingredient_Lightning_Essence",
                "CozyRanch_Snail_Void", "Ingredient_Void_Essence");
            want.forEach((role, item) -> {
                assertEquals(item, s.productsFor(role).getFirst().Item, role);
                assertEquals(item, s.productsFor(s.RanchBabyRoleVariants.get(role)).getFirst().Item, role + " baby");
            });
            assertEquals(6, s.allProducts().size());
            for (SpeciesDefinition.Product p : s.allProducts()) {
                boolean slow = p.Item.contains("Lightning") || p.Item.contains("Void");
                assertTrue(slow ? p.IntervalMinutes > 60 : p.IntervalMinutes >= 40 && p.IntervalMinutes <= 60, p.Item);
            }
            assertFalse(s.BirthVariants.isEmpty());
        }
    }
}
