package com.hexvane.cozytalesranching.taming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.behaviour.FollowProgress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Following an owner who rides an NPC mount (fix wave 3, playtester report on Averion Mounts). */
class FollowAnchorTest {

    @Test
    void mountIdZeroOrNegativeMeansNotRiding() {
        assertFalse(FollowAnchor.rides(0));
        assertFalse(FollowAnchor.rides(-1));
        assertTrue(FollowAnchor.rides(1));
        assertTrue(FollowAnchor.rides(4711));
    }

    @Test
    void followsTheMountWhileRidingAndTheOwnerOtherwise() {
        assertEquals("mount", FollowAnchor.choose("owner", "mount"));
        assertEquals("owner", FollowAnchor.choose("owner", null));
    }

    @Test
    void aMountOutrunningTheAnimalCountsAsStuckSoTheCatchUpTeleportFires() {
        // The owner on a mount pulls away 8 blocks a second; the animal walks 4 blocks a second after it.
        double stuck = 0;
        double animal = 0;
        double rider = 6;
        for (int s = 0; s < 6; s++) {
            double before = rider - animal;
            rider += 8;
            animal += 4;
            stuck = FollowProgress.stuckSeconds(before, rider - animal, 1.0, stuck);
        }
        assertTrue(stuck >= 6, "no progress for six seconds brings the animal back, got " + stuck);
    }

    @Test
    void tameClaimAndFollowLinesSayHowToMakeItStay() throws Exception {
        Map<String, String> lang = new HashMap<>();
        for (String line : Files.readAllLines(Path.of("src/main/resources/Server/Languages/en-US/cozyranch_taming.lang"), StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq > 0) lang.put(line.substring(0, eq).trim(), line.substring(eq + 1));
        }
        for (String key : new String[] {"tame.done", "claim.done", "follow.on"}) {
            String text = lang.get(key);
            assertTrue(text != null && text.contains("crouch and use it"), key + " explains how to stop following: " + text);
            assertTrue(text.contains("{animal}"), key + " keeps its {animal} parameter");
        }
    }
}
