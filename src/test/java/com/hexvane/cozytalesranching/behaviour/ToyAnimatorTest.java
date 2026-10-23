package com.hexvane.cozytalesranching.behaviour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.cozytalesranching.behaviour.ToyAnimator.Cell;
import com.hexvane.cozytalesranching.behaviour.ToyAnimator.Plan;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Toys animate while an animal plays with them: which toys change state, and the Play clips the client will load. */
class ToyAnimatorTest {
    private static final Cell A = new Cell(1, 64, 1);
    private static final Cell B = new Cell(4, 64, 2);
    private static final Cell C = new Cell(-3, 65, 7);
    private static final List<String> TOYS = List.of("Ball", "ScratchingPost", "HangingBell", "FloatingDuck", "Log");

    @Test
    void aToyStartsWhenAnAnimalStartsPlayingAndStopsAfter() {
        Plan start = ToyAnimator.plan(Set.of(), Set.of(A), Set.of());
        assertEquals(Set.of(A), start.start());
        assertTrue(start.stop().isEmpty());

        Plan during = ToyAnimator.plan(Set.of(A), Set.of(A), Set.of());
        assertTrue(during.start().isEmpty(), "nothing is resent while it plays");
        assertTrue(during.stop().isEmpty());

        Plan end = ToyAnimator.plan(Set.of(A), Set.of(), Set.of());
        assertTrue(end.start().isEmpty());
        assertEquals(Set.of(A), end.stop());
    }

    @Test
    void twoAnimalsOnOneToyKeepItPlayingUntilBothStop() {
        // the wanted set holds cells, so a second session on the same toy changes nothing
        Set<Cell> wanted = new HashSet<>(List.of(A, A));
        assertEquals(1, wanted.size());
        assertTrue(ToyAnimator.plan(Set.of(A), wanted, Set.of()).stop().isEmpty());
    }

    @Test
    void aToyLeftPlayingByARestartIsPutBackUnlessInUse() {
        Plan p = ToyAnimator.plan(Set.of(), Set.of(B), Set.of(B, C));
        assertEquals(Set.of(B), p.start(), "in use: started (and so tracked) rather than reset");
        assertEquals(Set.of(C), p.stop());
    }

    @Test
    void playStateIdsAreRecognised() {
        assertTrue(ToyAnimator.isPlayState("*CozyRanch_Toy_Ball_State_Definitions_Play"));
        assertFalse(ToyAnimator.isPlayState("CozyRanch_Toy_Ball"));
        assertFalse(ToyAnimator.isPlayState("*CozyRanch_Trough_State_Definitions_Filled"));
        assertFalse(ToyAnimator.isPlayState(null));
        assertEquals("CozyRanch_Toy_Ball", BehaviourConfig.baseBlockId("*CozyRanch_Toy_Ball_State_Definitions_Play"),
            "a playing toy is still found by the toy scan");
    }

    @Test
    void everyToyHasALoopingPlayClipTheClientAccepts() throws Exception {
        for (String toy : TOYS) {
            JsonObject item = json("/Server/Item/Items/CozyRanch/Toys/CozyRanch_Toy_" + toy + ".json");
            JsonObject play = item.getAsJsonObject("BlockType").getAsJsonObject("State").getAsJsonObject("Definitions").getAsJsonObject(ToyAnimator.STATE_PLAY);
            assertNotNull(play, toy + " has a Play state");
            assertTrue(play.get("Looping").getAsBoolean(), toy + " loops while played with");
            String clip = play.get("CustomModelAnimation").getAsString();
            assertEquals("Blocks/CozyRanch/Toys/Toy_" + toy + "_Play.blockyanim", clip);

            JsonObject anim = json("/Common/" + clip);
            int duration = anim.get("duration").getAsInt();
            assertEquals(60, duration, toy + ": one second, the planner's pass, so a stop lands near rest");
            Set<String> nodes = new HashSet<>();
            collect(json("/Common/Blocks/CozyRanch/Toys/Toy_" + toy + ".blockymodel").getAsJsonArray("nodes"), nodes);
            JsonObject byNode = anim.getAsJsonObject("nodeAnimations");
            assertFalse(byNode.isEmpty(), toy + " animates something");
            for (String node : byNode.keySet()) {
                assertTrue(nodes.contains(node), toy + ": " + node + " is a node of the model");
                JsonObject n = byNode.getAsJsonObject(node);
                for (String ch : List.of("position", "orientation", "shapeStretch", "shapeVisible", "shapeUvOffset")) {
                    assertTrue(n.has(ch) && n.get(ch).isJsonArray(), toy + ": " + node + " carries " + ch);
                    int last = -1;
                    for (JsonElement k : n.getAsJsonArray(ch)) {
                        double t = k.getAsJsonObject().get("time").getAsDouble();
                        assertEquals(Math.rint(t), t, toy + ": whole-number key times");
                        assertTrue(t > last && t <= duration, toy + ": keys rise within the clip");
                        last = (int) t;
                    }
                }
                // the clip starts and ends at rest, so the swap to Play and back to default does not pop
                JsonArray pos = n.getAsJsonArray("position");
                JsonArray rot = n.getAsJsonArray("orientation");
                assertRest(toy, node, pos.get(0), rot.get(0));
                assertRest(toy, node, pos.get(pos.size() - 1), rot.get(rot.size() - 1));
            }
        }
    }

    private static void assertRest(String toy, String node, JsonElement p, JsonElement r) {
        JsonObject d = p.getAsJsonObject().getAsJsonObject("delta");
        assertEquals(0, Math.abs(d.get("x").getAsDouble()) + Math.abs(d.get("y").getAsDouble()) + Math.abs(d.get("z").getAsDouble()), 1e-4,
            toy + ": " + node + " position at rest");
        JsonObject q = r.getAsJsonObject().getAsJsonObject("delta");
        assertEquals(1, Math.abs(q.get("w").getAsDouble()), 1e-4, toy + ": " + node + " orientation at rest");
    }

    private static void collect(JsonArray arr, Set<String> out) {
        if (arr == null) return;
        for (JsonElement e : arr) {
            JsonObject n = e.getAsJsonObject();
            out.add(n.get("name").getAsString());
            collect(n.getAsJsonArray("children"), out);
        }
    }

    private static JsonObject json(String resource) throws Exception {
        try (InputStream in = ToyAnimatorTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
