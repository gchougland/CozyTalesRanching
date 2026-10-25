package com.hexvane.cozytalesranching.taming;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleMatch;
import org.junit.jupiter.api.Test;

/** Fix 4 perf: the hint repair skips every NPC that is not one of our wild roles before it copies any update list. */
class WildPromptsFilterTest {
    @Test
    void onlyWildRolesPass() {
        SpeciesDefinition s = new SpeciesDefinition();
        s.Id = "cow";
        assertTrue(WildPrompts.isWildRanchKind(new RoleMatch(s, RoleKind.WILD)));
        assertTrue(WildPrompts.isWildRanchKind(new RoleMatch(s, RoleKind.WILD_BABY)));
        assertFalse(WildPrompts.isWildRanchKind(new RoleMatch(s, RoleKind.RANCH)));
        assertFalse(WildPrompts.isWildRanchKind(new RoleMatch(s, RoleKind.ADOPT)));
        assertFalse(WildPrompts.isWildRanchKind(null));
        assertFalse(WildPrompts.isWildRanchRole(null));
        assertFalse(WildPrompts.isWildRanchRole("Villager_Some_Unknown_Role"));
    }
}
