package com.hexvane.cozytalesranching.behaviour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.behaviour.BehaviourRules.Context;
import com.hexvane.cozytalesranching.behaviour.BehaviourRules.GroundSample;
import com.hexvane.cozytalesranching.behaviour.BehaviourRules.Option;
import com.hexvane.cozytalesranching.behaviour.BehaviourRules.Snapshot;
import com.hexvane.cozytalesranching.behaviour.BehaviourRules.Verdict;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BehaviourRulesTest {
    private static BehaviourConfig config;

    private static final Set<String> COW_CLIPS = Set.of("Idle", "Walk", "Sleep", "Curious", "Happy", "EatTrough", "Love", "Graze", "TailSwish");
    private static final Set<String> CHICK_CLIPS = Set.of("Alerted", "Hurt", "Death", "GreetAnimation", "Happy");
    private static final Set<String> FROG_CLIPS = Set.of("Idle", "Sleep", "Curious", "Happy", "EatTrough", "Love", "LilyHop", "CroakSit");

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = BehaviourRulesTest.class.getResourceAsStream("/Server/CozyTalesRanching/Behaviour.json")) {
            assertNotNull(in, "bundled Behaviour.json");
            config = BehaviourConfig.read(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    // ---- the bundled file ----

    @Test
    void bundledFileHasEveryContractBehaviourToyAndPersonality() {
        for (String id : List.of("trough_snack", "graze", "tail_swish", "dust_bathe", "peck", "flap", "mud_roll", "snuffle", "pronk",
            "lily_hop", "croak", "follow_parent", "socialise", "nap", "toy_ball", "toy_post", "toy_bell", "toy_duck", "toy_log")) {
            assertNotNull(config.def(id), id);
            assertTrue(config.def(id).Event, id + " fires the event");
        }
        assertEquals("toy_ball", config.toyBehaviour("CozyRanch_Toy_Ball"));
        assertEquals("toy_post", config.toyBehaviour("CozyRanch_Toy_ScratchingPost"));
        assertEquals("toy_bell", config.toyBehaviour("*CozyRanch_Toy_HangingBell_State_Definitions_Ring"));
        assertEquals("toy_duck", config.toyBehaviour("CozyRanch_Toy_FloatingDuck"));
        assertEquals("toy_log", config.toyBehaviour("CozyRanch_Toy_Log"));
        assertEquals(Set.copyOf(BehaviourRules.PERSONALITIES), config.Personalities.keySet());
    }

    @Test
    void everySpeciesBehaviourIsDefinedAndBabiesFollowParents() {
        for (String species : List.of("cow", "chicken", "pig", "sheep", "frog")) {
            assertTrue(config.Species.containsKey(species), species);
            for (boolean baby : new boolean[] {false, true}) {
                for (String id : config.behavioursOf(species, baby).keySet()) assertNotNull(config.def(id), species + " " + id);
            }
            assertTrue(config.behavioursOf(species, true).containsKey("follow_parent"), species + " babies follow");
            assertFalse(config.behavioursOf(species, false).containsKey("follow_parent"), species + " adults do not");
            assertTrue(config.behavioursOf(species, false).containsKey("trough_snack"), species + " snacks");
        }
        assertTrue(config.behavioursOf("wolf", false).isEmpty());
    }

    // ---- who may act ----

    private static Snapshot idle() {
        return new Snapshot(true, true, true, false, "Idle", false, true, true, 3, 12, 600);
    }

    private static Snapshot with(String state, boolean following, boolean daytime) {
        Snapshot s = idle();
        return new Snapshot(s.owned(), s.housed(), s.active(), following, state, s.roleChanging(), s.ranchRole(), daytime, s.distanceToDoor(), s.homeRange(), s.secondsSinceInteraction());
    }

    @Test
    void onlyAnIdleOwnedResidentInRangeByDayMayStart() {
        assertEquals(Verdict.OK, BehaviourRules.canStart(idle(), config));
        assertEquals(Verdict.FOLLOWING, BehaviourRules.canStart(with("Follow", true, true), config));
        assertEquals(Verdict.NOT_IDLE, BehaviourRules.canStart(with("Stay", false, true), config));
        assertEquals(Verdict.NOT_IDLE, BehaviourRules.canStart(with("GoHome", false, true), config));
        assertEquals(Verdict.NOT_IDLE, BehaviourRules.canStart(with("$Harvest", false, true), config));
        assertEquals(Verdict.NIGHT, BehaviourRules.canStart(with("Idle", false, false), config));
        assertEquals(Verdict.NOT_OWNED, BehaviourRules.canStart(new Snapshot(false, true, true, false, "Idle", false, true, true, 3, 12, 600), config));
        assertEquals(Verdict.NOT_HOUSED, BehaviourRules.canStart(new Snapshot(true, false, true, false, "Idle", false, true, true, 3, 12, 600), config));
        assertEquals(Verdict.NOT_ACTIVE, BehaviourRules.canStart(new Snapshot(true, true, false, false, "Idle", false, true, true, 3, 12, 600), config));
        assertEquals(Verdict.ROLE_CHANGING, BehaviourRules.canStart(new Snapshot(true, true, true, false, "Idle", true, true, true, 3, 12, 600), config));
        assertEquals(Verdict.NOT_RANCH_ROLE, BehaviourRules.canStart(new Snapshot(true, true, true, false, "Idle", false, false, true, 3, 12, 600), config));
        assertEquals(Verdict.OUT_OF_RANGE, BehaviourRules.canStart(new Snapshot(true, true, true, false, "Idle", false, true, true, 13, 12, 600), config));
        assertEquals(Verdict.RECENT_INTERACTION, BehaviourRules.canStart(new Snapshot(true, true, true, false, "Idle", false, true, true, 3, 12, 2), config));
    }

    @Test
    void aRunningBehaviourStopsWhenAnythingElseTakesTheAnimal() {
        for (String own : List.of("Behave", "Tag", "Meet")) assertEquals(Verdict.OK, BehaviourRules.canContinue(with(own, false, true)), own);
        assertEquals(Verdict.FOLLOWING, BehaviourRules.canContinue(with("Behave", true, true)));
        assertEquals(Verdict.NIGHT, BehaviourRules.canContinue(with("Tag", false, false)));
        assertEquals(Verdict.NOT_IDLE, BehaviourRules.canContinue(with("GoHome", false, true)));
        assertEquals(Verdict.NOT_IDLE, BehaviourRules.canContinue(with("Stay", false, true)));
        assertEquals(Verdict.NOT_IDLE, BehaviourRules.canContinue(with("Follow", false, true)));
        assertEquals(Verdict.NOT_IDLE, BehaviourRules.canContinue(with("Idle", false, true)));
        assertEquals(Verdict.ROLE_CHANGING, BehaviourRules.canContinue(new Snapshot(true, true, true, false, "Behave", true, true, true, 3, 12, 600)));
    }

    @Test
    void daytimeStopsBeforeTheHouseSendsResidentsHome() {
        assertFalse(BehaviourRules.isDaytime(0.0));
        assertFalse(BehaviourRules.isDaytime(0.23), "the house brings them out at 0.23; give it a moment");
        assertTrue(BehaviourRules.isDaytime(0.25));
        assertTrue(BehaviourRules.isDaytime(0.5));
        assertTrue(BehaviourRules.isDaytime(0.749));
        assertFalse(BehaviourRules.isDaytime(0.75));
        assertFalse(BehaviourRules.isDaytime(0.77), "dusk");
    }

    // ---- what it may pick ----

    private static Context ctx(String species, boolean baby, String personality, Set<String> clips) {
        return new Context(species, baby, personality, clips, Set.of(), false, false, false, Set.of(), false, false, false, false);
    }

    private static Map<String, Double> weights(List<Option> options) {
        return options.stream().collect(Collectors.toMap(Option::id, Option::weight));
    }

    @Test
    void aSpeciesDoesOnlyItsOwnBehaviours() {
        Context cow = new Context("cow", false, null, COW_CLIPS, Set.of("grass"), false, false, true, Set.of("toy_ball", "toy_duck"), false, false, false, false);
        Map<String, Double> w = weights(BehaviourRules.options(cow, config, id -> false));
        assertTrue(w.containsKey("graze"));
        assertTrue(w.containsKey("tail_swish"));
        assertTrue(w.containsKey("trough_snack"));
        assertTrue(w.containsKey("toy_ball"));
        assertFalse(w.containsKey("toy_duck"), "cows do not care for the floating duck");
        assertFalse(w.containsKey("peck"));
        assertFalse(w.containsKey("mud_roll"));
        assertFalse(w.containsKey("lily_hop"));
        assertFalse(w.containsKey("follow_parent"), "adults do not follow parents");
        assertFalse(w.containsKey("retreat"), "a reaction, never picked");
    }

    @Test
    void groundTroughToysCompanyAndPlayersMustBeThere() {
        Map<String, Double> w = weights(BehaviourRules.options(ctx("cow", false, null, COW_CLIPS), config, id -> false));
        assertFalse(w.containsKey("graze"), "no grass under it");
        assertFalse(w.containsKey("trough_snack"), "no trough with food");
        assertFalse(w.containsKey("toy_ball"), "no toy");
        assertFalse(w.containsKey("socialise"), "nobody to meet");
        assertFalse(w.containsKey("nap"), "no shade and not near the house");
        assertTrue(w.containsKey("tail_swish"), "needs nothing");
        Context calf = new Context("cow", true, null, Set.of("Happy", "Sleep"), Set.of(), false, true, false, Set.of(), true, true, false, false);
        Map<String, Double> b = weights(BehaviourRules.options(calf, config, id -> false));
        assertTrue(b.containsKey("follow_parent"));
        assertTrue(b.containsKey("socialise"));
        assertTrue(b.containsKey("nap"), "near the house");
        assertFalse(b.containsKey("trough_snack"), "babies are fed, they do not snack");
    }

    @Test
    void aClipTheModelLacksRulesTheBehaviourOutUnlessItMayStandStill() {
        Context chick = new Context("chicken", true, null, CHICK_CLIPS, Set.of(), true, true, false, Set.of(), true, true, false, false);
        List<Option> options = BehaviourRules.options(chick, config, id -> false);
        Option nap = options.stream().filter(o -> o.id().equals("nap")).findFirst().orElse(null);
        assertNotNull(nap, "the chick has no Sleep clip but may nap standing still");
        assertNull(nap.animation());
        Option social = options.stream().filter(o -> o.id().equals("socialise")).findFirst().orElseThrow();
        assertEquals("Happy", social.animation());
        Context frog = new Context("frog", false, null, Set.of("Idle"), Set.of("pond"), false, false, false, Set.of(), false, false, false, false);
        assertFalse(weights(BehaviourRules.options(frog, config, id -> false)).containsKey("lily_hop"), "no LilyHop clip on this model");
        Context real = new Context("frog", false, null, FROG_CLIPS, Set.of("pond"), false, false, false, Set.of(), false, false, false, false);
        assertEquals("LilyHop", BehaviourRules.options(real, config, id -> false).stream().filter(o -> o.id().equals("lily_hop")).findFirst().orElseThrow().animation());
    }

    @Test
    void cooldownsRuleBehavioursOut() {
        Context cow = new Context("cow", false, null, COW_CLIPS, Set.of("grass"), false, false, true, Set.of(), false, false, false, false);
        Map<String, Double> w = weights(BehaviourRules.options(cow, config, id -> id.equals("graze")));
        assertFalse(w.containsKey("graze"));
        assertTrue(w.containsKey("trough_snack"));
        BehaviourConfig.Def snack = config.def("trough_snack");
        assertEquals((long) (0.25 * 1800 * 1000), BehaviourRules.cooldownMs(snack, 1800), "a quarter of a 30 minute day: at most four snacks a day");
        assertEquals(60_000, BehaviourRules.cooldownMs(config.def("graze"), 1800));
        assertTrue(BehaviourRules.coolingDown(1_000L, 60_000, 30_000));
        assertFalse(BehaviourRules.coolingDown(1_000L, 60_000, 61_000));
        assertFalse(BehaviourRules.coolingDown(null, 60_000, 0));
    }

    @Test
    void eventsAtMostOncePerAnimalPerBehaviourPerMinute() {
        assertTrue(BehaviourRules.eventAllowed(null, config, 0));
        assertFalse(BehaviourRules.eventAllowed(1_000L, config, 60_999));
        assertTrue(BehaviourRules.eventAllowed(1_000L, config, 61_000));
    }

    // ---- personalities ----

    @Test
    void personalitiesChangeWeights() {
        assertEquals(3.0, BehaviourRules.personalityMultiplier(config, "lazy", "nap"));
        assertEquals(0.5, BehaviourRules.personalityMultiplier(config, "lazy", "toy_ball"));
        assertEquals(3.0, BehaviourRules.personalityMultiplier(config, "playful", "toy_log"), "a prefix entry");
        assertEquals(1.0, BehaviourRules.personalityMultiplier(config, "playful", "graze"));
        assertEquals(1.0, BehaviourRules.personalityMultiplier(config, null, "nap"));
        assertEquals(1.0, BehaviourRules.personalityMultiplier(config, "grumpy", "nap"), "unknown personality");
        assertEquals(0.0, BehaviourRules.personalityMultiplier(config, "shy", "approach_player"));

        Context base = new Context("cow", false, "lazy", COW_CLIPS, Set.of(), true, true, false, Set.of("toy_ball"), false, false, true, true);
        Map<String, Double> lazy = weights(BehaviourRules.options(base, config, id -> false));
        Map<String, Double> playful = weights(BehaviourRules.options(new Context("cow", false, "playful", COW_CLIPS, Set.of(), true, true, false, Set.of("toy_ball"), false, false, true, true), config, id -> false));
        assertTrue(lazy.get("nap") > playful.get("nap"), "lazy naps more");
        assertTrue(playful.get("toy_ball") > lazy.get("toy_ball"), "playful plays more");
        assertTrue(BehaviourRules.decisionSeconds(config, "lazy", 0.5) > BehaviourRules.decisionSeconds(config, "playful", 0.5));
        assertTrue(BehaviourRules.idleWeight(config, "lazy") > BehaviourRules.idleWeight(config, null));
    }

    @Test
    void personalityMovesBelongToTheirPersonality() {
        Context near = new Context("cow", false, "curious", COW_CLIPS, Set.of(), false, false, false, Set.of(), false, false, true, true);
        Map<String, Double> curious = weights(BehaviourRules.options(near, config, id -> false));
        assertTrue(curious.containsKey("approach_player"));
        assertFalse(curious.containsKey("greet_owner"));
        Map<String, Double> affectionate = weights(BehaviourRules.options(new Context("cow", false, "affectionate", COW_CLIPS, Set.of(), false, false, false, Set.of(), false, false, true, true), config, id -> false));
        assertTrue(affectionate.containsKey("greet_owner"));
        assertFalse(affectionate.containsKey("approach_player"));
        Map<String, Double> none = weights(BehaviourRules.options(new Context("cow", false, null, COW_CLIPS, Set.of(), false, false, false, Set.of(), false, false, true, true), config, id -> false));
        assertFalse(none.containsKey("approach_player"));
        assertFalse(none.containsKey("greet_owner"));
        assertFalse(config.def("approach_player").Event, "not a contract id");
    }

    @Test
    void personalitiesRollUniformly() {
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < 5000; i++) seen.merge(BehaviourRules.rollPersonality(i / 5000.0), 1, Integer::sum);
        assertEquals(Set.copyOf(BehaviourRules.PERSONALITIES), seen.keySet());
        for (int n : seen.values()) assertEquals(1000, n, 1);
        assertEquals("affectionate", BehaviourRules.rollPersonality(1.0));
    }

    @Test
    void pickFollowsWeightsAndLeavesRoomForNothing() {
        List<Option> o = List.of(new Option("a", 1, null), new Option("b", 3, null));
        assertEquals("a", BehaviourRules.pick(o, 0, 0.1).id());
        assertEquals("b", BehaviourRules.pick(o, 0, 0.5).id());
        assertEquals("b", BehaviourRules.pick(o, 4, 0.45).id());
        assertNull(BehaviourRules.pick(o, 4, 0.6), "the idle share");
        assertNull(BehaviourRules.pick(List.of(), 2, 0.1));
    }

    // ---- ground ----

    private static GroundSample ground(String under, String at, String fluidAt, Set<String> nearBlocks, Set<String> nearFluids) {
        return new GroundSample(under, at, fluidAt, null, nearBlocks, nearFluids);
    }

    @Test
    void groundTagsFromTheCellsAroundTheAnimal() {
        assertEquals(Set.of("grass"), BehaviourRules.groundTags(ground("Soil_Grass_Sunny", null, null, Set.of(), Set.of()), config));
        assertEquals(Set.of("grass"), BehaviourRules.groundTags(ground("Rock_Stone", "Plant_Grass_Lush_Short", null, Set.of(), Set.of()), config));
        assertEquals(Set.of(), BehaviourRules.groundTags(ground("Soil_Grass_Burnt", null, null, Set.of(), Set.of()), config));
        assertEquals(Set.of("dust"), BehaviourRules.groundTags(ground("Soil_Sand_White", null, null, Set.of(), Set.of()), config));
        assertEquals(Set.of("dust"), BehaviourRules.groundTags(ground("Soil_Gravel_Mossy", null, null, Set.of(), Set.of()), config));
        assertEquals(Set.of("mud"), BehaviourRules.groundTags(ground("Soil_Dirt_Wet", null, null, Set.of(), Set.of()), config), "wet dirt is mud, not dust");
        assertEquals(Set.of("mud"), BehaviourRules.groundTags(ground("Soil_Mud", null, null, Set.of(), Set.of()), config));
        assertEquals(Set.of("dust"), BehaviourRules.groundTags(ground("Soil_Dirt", null, null, Set.of(), Set.of()), config), "dry dirt");
        assertTrue(BehaviourRules.groundTags(ground("Soil_Dirt", null, null, Set.of(), Set.of("Water_Source")), config).contains("mud"), "dirt by water");
        assertTrue(BehaviourRules.groundTags(ground("Soil_Grass", null, null, Set.of(), Set.of("Water")), config).containsAll(Set.of("grass", "mud", "pond")));
        assertEquals(Set.of("pond"), BehaviourRules.groundTags(ground(null, null, "Water", Set.of(), Set.of()), config), "standing in water");
        assertEquals(Set.of("pond"), BehaviourRules.groundTags(ground("Rock_Stone", null, null, Set.of("Plant_Flower_Water_Duckweed"), Set.of()), config), "beside a lily pad");
        assertEquals(Set.of(), BehaviourRules.groundTags(ground(null, null, null, Set.of(), Set.of()), config));
    }
}
