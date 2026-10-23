package com.hexvane.cozytalesranching.houseui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Renders every {@link HouseDemo} scenario through {@link HousePageLayout} and checks that
 * {@code tests/hytale/houseui.test.json} renders exactly those commands. Run with {@code HOUSEUI_WRITE_CASES=true}
 * to rewrite the test file after a layout change.
 */
class HousePageLayoutTest {
    static final UUID PLAYER = UUID.fromString("11111111-2222-4333-8444-555555555555");
    static final Path TEST_FILE = Path.of("tests/hytale/houseui.test.json");
    static final String PROJECT = "_worktrees/CozyTales-Ranching-fix-houseui";
    static final long NOW = 1_700_000_000_000L;
    static final String[][] RENDERS = {
        {"EMPTY", "empty pigsty, no products", "houseui-empty"},
        {"TYPICAL", "typical cow barn: three residents, one inside, one follower, no products", "houseui-typical"},
        {"FULL", "full chicken coop: six residents, long names, a baby, traits, eggs waiting, every resident action", "houseui-full"},
        {"UNCHECKED", "frog pond whose habitat has not been checked", "houseui-unchecked"},
        {"MISSING", "frog pond missing lily pads (needed) and reeds (liked)", "houseui-missing"},
        {"BREEDINGOFF", "sheepfold with breeding off", "houseui-breedingoff"},
        {"TYPICAL", "a button pressed before the house service is installed", "houseui-unavailable", "result.unavailable"},
        {"PRODUCTS", "seven kinds of products waiting, huge counts", "houseui-products"},
        {"MILK", "milk waiting and no buckets: the bucket hint", "houseui-milk"},
        {"MOVE", "move picker open for the first hen", "houseui-move"},
        {"PERSONALITY", "personalities and feed bonuses on residents (one bonus expired)", "houseui-personality"},
        {"LONG", "longest names everywhere, picker open", "houseui-long"},
        {"MILK", "after Collect with no buckets", "houseui-collected", "result.collect.buckets"},
        {"FULL", "area lights on: Hide area, the on notice", "houseui-border", "result.border.on.trough", "border"},
        {"TYPICAL", "no traits on any resident, a Refresh just done", "houseui-notraits", "result.refreshed"},
    };

    @TempDir static Path data;

    @BeforeAll
    static void loadSpecies() {
        SpeciesRegistry.load(data);
        com.hexvane.cozytalesranching.breeding.TraitRegistry.load(data);
    }

    @Test
    void fullCoopShowsEveryResidentAndBlocksMovingIn() {
        Recorder ui = render(HouseDemo.Scenario.FULL);
        assertEquals(6, ui.count("\"selector\":\"#ResidentList\",\"text\":\"CozyRanch/HouseResidentRow.ui\""));
        assertTrue(ui.has("#FollowerList[0] #FMoveIn.Disabled", "true"));
        assertTrue(ui.has("#ResidentList[5] #RBaby.Visible", "true"));
        assertTrue(ui.has("#ResidentList[0] #RHeart5.Visible", "true"));
        assertTrue(ui.has("#ResidentList[3] #RHeart3.Visible", "false"));
    }

    @Test
    void emptyHouseShowsTheInvitation() {
        Recorder ui = render(HouseDemo.Scenario.EMPTY);
        assertTrue(ui.has("#EmptyResidents.Visible", "true"));
        assertTrue(ui.has("#ResidentList.Visible", "false"));
        assertTrue(ui.has("#NoFollowers.Visible", "true"));
    }

    @Test
    void habitatSortsNeedsBeforeWishes() {
        HouseRecord house = HouseDemo.create(HouseDemo.Scenario.MISSING, PLAYER).house();
        SpeciesDefinition frog = SpeciesRegistry.byId("frog").orElseThrow();
        HouseView.Habitat h = HouseView.habitat(house, frog);
        assertEquals(HouseView.HabitatLevel.POOR, h.level());
        assertEquals(List.of("lily_pads"), h.missingRequired());
        assertEquals(List.of("reeds"), h.missingPreferred());
        house.habitatScannedAtMs = 0;
        assertEquals(HouseView.HabitatLevel.UNCHECKED, HouseView.habitat(house, frog).level());
    }

    @Test
    void moodAndHeartsAreClamped() {
        assertEquals(HouseView.Mood.HAPPY, HouseView.mood(0.7f));
        assertEquals(HouseView.Mood.OKAY, HouseView.mood(0.4f));
        assertEquals(HouseView.Mood.SAD, HouseView.mood(0.39f));
        assertEquals(5, HouseView.hearts(9));
        assertEquals(0, HouseView.hearts(-1));
    }

    @Test
    void namesAreCleaned() {
        assertEquals("Sunny Roost", HouseView.cleanName("  Sunny \t  Roost "));
        assertEquals(null, HouseView.cleanName("   "));
        assertEquals(24, HouseView.cleanName("x".repeat(40)).length());
    }

    @Test
    void everyHabitatIdHasALangKey() throws IOException {
        String lang = Files.readString(Path.of("src/main/resources/Server/Languages/en-US/cozyranch_houseui.lang"), StandardCharsets.UTF_8);
        for (SpeciesDefinition s : SpeciesRegistry.all()) {
            for (String id : concat(s.HabitatRequires, s.HabitatPrefers)) {
                assertTrue(Pattern.compile("(?m)^habitat\\." + Pattern.quote(id) + "\\s*=").matcher(lang).find(), "no lang key for habitat " + id);
            }
        }
    }

    @Test
    void testFileRendersWhatTheJavaSends() throws IOException {
        String generated = testFile();
        if ("true".equals(System.getenv("HOUSEUI_WRITE_CASES"))) {
            Files.writeString(TEST_FILE, generated, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(TEST_FILE), "run with HOUSEUI_WRITE_CASES=true to create " + TEST_FILE);
        String committed = Files.readString(TEST_FILE, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?m)^\\s*\"commands\": (\\[.*]),?$").matcher(generated);
        int found = 0;
        while (m.find()) {
            found++;
            assertTrue(committed.contains(m.group(1)), "houseui.test.json is out of date with HousePageLayout; rerun with HOUSEUI_WRITE_CASES=true");
        }
        assertEquals(RENDERS.length, found);
        assertFalse(committed.contains("\"commands\": []"));
    }

    static Recorder render(HouseDemo.Scenario scenario) {
        return render(scenario, null);
    }

    static Recorder render(HouseDemo.Scenario scenario, String noticeKey) {
        return render(scenario, noticeKey, false);
    }

    static Recorder render(HouseDemo.Scenario scenario, String noticeKey, boolean border) {
        HouseDemo demo = HouseDemo.create(scenario, PLAYER);
        Recorder ui = new Recorder();
        HousePageLayout.render(view(demo, noticeKey, border), ui);
        return ui;
    }

    /** The view {@code HousePage.view} builds, with the house item names read from the item files. */
    static HouseView view(HouseDemo demo, String noticeKey) {
        return view(demo, noticeKey, false);
    }

    static HouseView view(HouseDemo demo, String noticeKey, boolean border) {
        HouseRecord house = demo.house();
        SpeciesDefinition species = SpeciesRegistry.byId(house.speciesId).orElseThrow();
        Txt speciesName = HouseView.speciesName(species, house.speciesId);
        List<HouseView.MoveTarget> targets = demo.moveTargets(house).stream()
            .map(h -> new HouseView.MoveTarget(h.houseId, HouseView.pickerName(h, speciesName, houseNameKey(h.blockId)),
                demo.residents(h).size(), Math.max(1, h.capacity)))
            .toList();
        return HouseView.of(house, species, houseNameKey(house.blockId), demo.residents(house), demo.service()::isInside,
            demo.followers(house, PLAYER), noticeKey == null ? null : Txt.ui(noticeKey),
            demo.stored(house), demo.initialState(), targets, r -> PLAYER.equals(r.ownerId), border, NOW);
    }

    @Test
    void productsShowFourChipsAndTheRestAsMore() {
        Recorder ui = render(HouseDemo.Scenario.PRODUCTS);
        assertEquals(HousePageLayout.MAX_CHIPS - 1, ui.count("\"selector\":\"#ProductList\",\"text\":\"CozyRanch/HouseProductChip.ui\""));
        assertTrue(ui.has("#ProductsMore.Visible", "true"));
        assertTrue(ui.has("#ProductList[0] #PIcon.ItemId", "\"Food_Egg\""));
        assertTrue(ui.has("#CollectButton.Disabled", "false"));
        assertTrue(ui.has("#MilkHint.Visible", "false"));
    }

    @Test
    void noProductsDisablesCollect() {
        Recorder ui = render(HouseDemo.Scenario.TYPICAL);
        assertTrue(ui.has("#ProductsEmpty.Visible", "true"));
        assertTrue(ui.has("#CollectButton.Disabled", "true"));
        assertEquals(0, ui.count("HouseProductChip.ui"));
    }

    @Test
    void milkShowsTheBucketHintAndStaysAfterCollect() {
        HouseDemo demo = HouseDemo.create(HouseDemo.Scenario.MILK, PLAYER);
        assertTrue(view(demo, null).bucketsNeeded());
        assertEquals(0, demo.collect(null, null, demo.house()));
        assertEquals(Map.of(HouseDemo.MILK, 3), demo.stored(demo.house()));
        assertTrue(render(HouseDemo.Scenario.MILK).has("#MilkHint.Visible", "true"));
    }

    @Test
    void moveIsHiddenWithoutAnotherHouseWithRoom() {
        assertTrue(render(HouseDemo.Scenario.TYPICAL).has("#MoveButton.Visible", "false"));
        Recorder full = render(HouseDemo.Scenario.FULL);
        assertTrue(full.has("#MoveButton.Visible", "true"));
        assertTrue(full.has("#MoveCard.Visible", "false"));
        Recorder move = render(HouseDemo.Scenario.MOVE);
        assertTrue(move.has("#MoveCard.Visible", "true"));
        assertTrue(move.has("#DetailCard.Visible", "false"));
        assertEquals(2, move.count("\"selector\":\"#MoveList\",\"text\":\"CozyRanch/HouseMoveRow.ui\""), "the full coop is left out");
    }

    @Test
    void firstResidentIsSelectedByDefault() {
        Recorder ui = render(HouseDemo.Scenario.FULL);
        assertTrue(ui.has("#ResidentList[0] #RSelected.Visible", "true"));
        assertTrue(ui.has("#ResidentList[1] #RSelected.Visible", "false"));
        assertTrue(ui.has("#DetailName.TextSpans", "{\"RawText\":\"Princess Featherbottom\"}"));
    }

    @Test
    void personalityAndActiveFeedBonusShow() {
        Recorder ui = render(HouseDemo.Scenario.PERSONALITY);
        assertTrue(ui.has("#ResidentList[0] #RPersonality.TextSpans", "{\"MessageId\":\"" + HouseView.PERSONALITY_LANG + "playful\"}"));
        assertTrue(ui.has("#ResidentList[0] #RFeedText.TextSpans", "{\"MessageId\":\"cozyranch_houseui.feed.hearty\"}"));
        assertTrue(ui.has("#ResidentList[2] #RFeed.Visible", "false"), "Rosie's sweet feed has worn off");
        assertTrue(ui.has("#ResidentList[0] #RPersonality.Visible", "true"));
        assertTrue(render(HouseDemo.Scenario.TYPICAL).has("#ResidentList[0] #RPersonality.Visible", "false"));
    }

    @Test
    void moveTargetsAreSameOwnerSpeciesWorldWithRoom() {
        HouseRecord a = house("cow", "default", 4), b = house("cow", "default", 4);
        assertTrue(HouseView.canMoveTo(a, b, 3));
        assertFalse(HouseView.canMoveTo(a, b, 4));
        assertFalse(HouseView.canMoveTo(a, a, 0));
        assertFalse(HouseView.canMoveTo(a, house("pig", "default", 4), 0));
        assertFalse(HouseView.canMoveTo(a, house("cow", "other", 4), 0));
        HouseRecord stranger = house("cow", "default", 4);
        stranger.ownerId = UUID.randomUUID();
        assertFalse(HouseView.canMoveTo(a, stranger, 0));
    }

    @Test
    void locateSaysWhereAndHowFar() {
        Txt name = Txt.raw("Daisy");
        Locate.Fix me = new Locate.Fix(0, 64, 0, true);
        assertEquals("cozyranch_houseui.locate.inside", Locate.describe(name, true, AnimalRecord.State.ACTIVE, true, null, me).key());
        assertEquals("cozyranch_houseui.locate.lost", Locate.describe(name, false, AnimalRecord.State.MISSING, true, null, me).key());
        assertEquals("cozyranch_houseui.locate.elsewhere", Locate.describe(name, false, AnimalRecord.State.ACTIVE, false, null, me).key());
        assertEquals("cozyranch_houseui.locate.near", Locate.describe(name, false, AnimalRecord.State.ACTIVE, true, new Locate.Fix(1, 64, 1, true), me).key());
        Txt far = Locate.describe(name, false, AnimalRecord.State.ACTIVE, true, new Locate.Fix(30, 64, -40, true), me);
        assertEquals("cozyranch_houseui.locate.far", far.key());
        assertEquals(50, far.params().get("distance"));
        assertEquals("cozyranch_houseui.dir.northeast", ((Txt) far.params().get("direction")).key());
        assertEquals("cozyranch_houseui.locate.last", Locate.describe(name, false, AnimalRecord.State.ACTIVE, true, new Locate.Fix(0, 64, 20, false), me).key());
        assertEquals("north", Locate.compass(0, -5));
        assertEquals("east", Locate.compass(5, 0));
        assertEquals("south", Locate.compass(0, 5));
        assertEquals("west", Locate.compass(-5, 0));
        assertEquals("southwest", Locate.compass(-5, 5));
    }

    @Test
    void eachTraitIsAPillWithItsDescriptionAsTooltip() {
        Recorder ui = render(HouseDemo.Scenario.FULL);
        int rowWithTwo = -1;
        for (int i = 0; i < 6 && rowWithTwo < 0; i++) if (ui.has("#ResidentList[" + i + "] #RTrait2.Visible", "true")) rowWithTwo = i;
        assertTrue(rowWithTwo >= 0, "the full coop has a resident with two traits");
        String row = "#ResidentList[" + rowWithTwo + "] ";
        assertTrue(ui.has(row + "#RTrait1.Visible", "true"));
        for (String pill : new String[] {"#RTrait1", "#RTrait2"}) {
            assertEquals(1, ui.count(row + pill + ".TooltipText\",\"data\":{\"MessageId\":\"cozyranch_breeding.trait."), pill);
            assertEquals(1, ui.count(row + pill + ".TextSpans\""), pill);
        }
        assertTrue(ui.count(".desc\"}") >= 2, "tooltips are the trait descriptions");
    }

    @Test
    void noTraitsHidesBothPills() {
        Recorder ui = render(HouseDemo.Scenario.TYPICAL);
        for (int i = 0; i < 3; i++) {
            if (!ui.has("#ResidentList[" + i + "] #RTrait1.Visible", "false")) continue;
            assertTrue(ui.has("#ResidentList[" + i + "] #RTrait2.Visible", "false"));
            assertEquals(0, ui.count("#ResidentList[" + i + "] #RTrait1.TooltipText"));
            return;
        }
        throw new AssertionError("the typical barn has a resident with no traits");
    }

    @Test
    void traitsComeFromTraitsJsonWithAStockLineForUnknownIds() {
        HouseView.Trait known = HouseView.trait("bountiful");
        assertEquals("cozyranch_breeding.trait.bountiful.desc", known.description().key());
        HouseView.Trait unknown = HouseView.trait("moon_dancer");
        assertEquals("Moon dancer", unknown.name().raw());
        assertEquals("cozyranch_houseui.trait.nodesc", unknown.description().key());
    }

    @Test
    void nameButtonOnlyForTheViewersOwnAnimals() {
        HouseDemo demo = HouseDemo.create(HouseDemo.Scenario.FULL, PLAYER);
        Recorder mine = new Recorder();
        HousePageLayout.render(view(demo, null), mine);
        assertTrue(mine.has("#ResidentList[0] #RNameButton.Visible", "true"));
        assertTrue(mine.has("#ResidentList[0] #RNameButton.TextSpans", "{\"MessageId\":\"cozyranch_houseui.button.name\"}"));
        HouseRecord house = demo.house();
        SpeciesDefinition species = SpeciesRegistry.byId(house.speciesId).orElseThrow();
        HouseView stranger = HouseView.of(house, species, null, demo.residents(house), id -> false, List.of(), null, Map.of(),
            HouseView.PageState.NONE, List.of(), r -> false, false, NOW);
        Recorder theirs = new Recorder();
        HousePageLayout.render(stranger, theirs);
        assertTrue(theirs.has("#ResidentList[0] #RNameButton.Visible", "false"));
        assertEquals(0, theirs.count("#RNameButton.TextSpans"));
    }

    @Test
    void areaButtonSaysWhatPressingItDoes() {
        assertTrue(render(HouseDemo.Scenario.FULL).has("#AreaButton.TextSpans", "{\"MessageId\":\"cozyranch_houseui.button.area.show\"}"));
        assertTrue(render(HouseDemo.Scenario.FULL, null, true).has("#AreaButton.TextSpans", "{\"MessageId\":\"cozyranch_houseui.button.area.hide\"}"));
        assertTrue(render(HouseDemo.Scenario.FULL).has("#RefreshButton.TooltipText", "{\"MessageId\":\"cozyranch_houseui.tooltip.refresh\"}"));
    }

    @Test
    void refreshIsLimitedToOnceEveryTwoSecondsPerPlayer() {
        RateLimit limit = new RateLimit(2_000L);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(limit.tryAcquire(a, 10_000L));
        assertFalse(limit.tryAcquire(a, 10_500L));
        assertFalse(limit.tryAcquire(a, 11_999L));
        assertTrue(limit.tryAcquire(b, 11_999L), "another player is not held up");
        assertTrue(limit.tryAcquire(a, 12_000L));
        assertFalse(limit.tryAcquire(a, 12_001L), "a refused press does not reset the wait, the accepted one does");
        assertEquals(2_000L, HousePage.REFRESH_LIMIT_MS);
    }

    @Test
    void borderRingIsEvenAndOnTheGround() {
        List<double[]> ring = HouseBorder.ring(0.5, 0.5, 12.5, HouseBorder.SPACING);
        assertTrue(ring.size() >= 52);
        for (double[] p : ring) assertEquals(12.5, Math.hypot(p[0] - 0.5, p[1] - 0.5), 1e-9);
        double gap = Math.hypot(ring.get(1)[0] - ring.get(0)[0], ring.get(1)[1] - ring.get(0)[1]);
        assertTrue(gap <= HouseBorder.SPACING, "motes at most 1.5 blocks apart");
        assertTrue(HouseBorder.showTroughRing(12, 8));
        assertFalse(HouseBorder.showTroughRing(12, 12));
        // flat ground at 63: a mote stands at 64
        assertEquals(64, HouseBorder.groundY(column(y -> y <= 63, y -> true), 64));
        // a roof at 70 over ground at 63: the ground nearest the house's height wins
        assertEquals(64, HouseBorder.groundY(column(y -> y <= 63 || y == 70, y -> true), 64));
        // a hill: ground at 67
        assertEquals(68, HouseBorder.groundY(column(y -> y <= 67, y -> true), 64));
        // not in memory: no mote
        assertEquals(null, HouseBorder.groundY(column(y -> y <= 63, y -> y < 66), 64));
        // a drop far below the search: no mote
        assertEquals(null, HouseBorder.groundY(column(y -> y <= 40, y -> true), 64));
    }

    @Test
    void borderEndsWithTimeOrDistance() {
        HouseBorder.Active a = new HouseBorder.Active("default", UUID.randomUUID(), 0.5, 0.5, 36.5, List.of(), 1_000L + HouseBorder.LIFETIME_MS);
        assertTrue(HouseBorder.keep(a, 10, 10, 1_000L));
        assertFalse(HouseBorder.keep(a, 40, 0.5, 1_000L), "walked away");
        assertFalse(HouseBorder.keep(a, 10, 10, 1_000L + HouseBorder.LIFETIME_MS), "time is up");
    }

    static HouseBorder.Column column(java.util.function.IntPredicate ground, java.util.function.IntPredicate loaded) {
        return new HouseBorder.Column() {
            @Override public boolean loaded(int y) { return loaded.test(y); }
            @Override public boolean ground(int y) { return ground.test(y); }
        };
    }

    static HouseRecord house(String species, String world, int capacity) {
        HouseRecord h = new HouseRecord();
        h.houseId = UUID.randomUUID();
        h.ownerId = PLAYER;
        h.speciesId = species;
        h.worldName = world;
        h.capacity = capacity;
        return h;
    }

    /** What {@code Item.getTranslationKey} returns for a house item: its TranslationProperties name. */
    static String houseNameKey(String blockId) {
        try {
            String json = Files.readString(Path.of("src/main/resources/Server/Item/Items/CozyRanch/Houses/" + blockId + ".json"), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"Name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            return m.find() ? m.group(1) : null;
        } catch (IOException e) {
            return null;
        }
    }

    static String testFile() {
        String ui = PROJECT + "/src/main/resources/Common/UI/Custom/CozyRanch/HousePage.ui";
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"cases\": [\n");
        out.append("    {\n      \"name\": \"houseui gate\",\n      \"description\": \"Every UI document of the pack parses, and the page's Java names only ids and properties that exist.\",\n      \"steps\": [\n");
        out.append("        { \"tool\": \"asset_validate\", \"arguments\": { \"project\": \"" + PROJECT + "\" }, \"expect\": { \"valid\": true, \"schemaErrors\": 0 } },\n");
        out.append("        { \"tool\": \"ui_check_java\", \"arguments\": { \"project\": \"" + PROJECT + "\" }, \"expect\": { \"valid\": true } }\n");
        out.append("      ]\n    },\n");
        out.append("    {\n      \"name\": \"house page\",\n      \"description\": \"The house page for each /ranch housepage scenario, with the commands HousePageLayout sends (generated by HousePageLayoutTest).\",\n      \"steps\": [\n");
        for (int i = 0; i < RENDERS.length; i++) {
            String[] r = RENDERS[i];
            Recorder rec = render(HouseDemo.Scenario.valueOf(r[0]), r.length > 3 ? r[3] : null, r.length > 4 && r[4].equals("border"));
            out.append("        {\n");
            out.append("          \"tool\": \"ui_render\",\n");
            out.append("          \"name\": \"" + r[1] + "\",\n");
            out.append("          \"arguments\": {\n");
            out.append("            \"root\": \"projects\",\n");
            out.append("            \"path\": \"" + ui + "\",\n");
            out.append("            \"commands\": " + rec.json() + "\n");
            out.append("          },\n");
            out.append("          \"expect\": { \"valid\": true, \"errors\": 0, \"notes\": { \"$length\": 0 } },\n");
            out.append("          \"golden\": \"tests/hytale/golden/" + r[2] + ".png\"\n");
            out.append("        }" + (i + 1 < RENDERS.length ? "," : "") + "\n");
        }
        out.append("      ]\n    }\n  ]\n}\n");
        return out.toString();
    }

    static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    /** Records commands in the form the Hytale MCP's ui_render takes. Event bindings are not commands and are left out. */
    static final class Recorder implements UiOut {
        final List<String> commands = new ArrayList<>();

        @Override public void append(String document) { commands.add("{\"type\":\"Append\",\"text\":" + str(document) + "}"); }

        @Override public void append(String container, String document) {
            commands.add("{\"type\":\"Append\",\"selector\":" + str(container) + ",\"text\":" + str(document) + "}");
        }

        @Override public void clear(String selector) { commands.add("{\"type\":\"Clear\",\"selector\":" + str(selector) + "}"); }

        @Override public void set(String selector, boolean value) { commands.add(setCmd(selector, String.valueOf(value))); }

        @Override public void set(String selector, String value) { commands.add(setCmd(selector, str(value))); }

        @Override public void text(String selector, Txt text) { commands.add(setCmd(selector + ".TextSpans", message(text))); }

        @Override public void tooltip(String selector, Txt text) { commands.add(setCmd(selector + ".TooltipText", message(text))); }

        @Override public void click(String selector, String action, String animal, String house) {}

        @Override public void clickWithField(String selector, String action, String fieldSelector) {}

        boolean has(String selector, String data) {
            return commands.contains(setCmd(selector, data));
        }

        int count(String fragment) {
            return (int) commands.stream().filter(c -> c.contains(fragment)).count();
        }

        String json() {
            return "[" + String.join(", ", commands) + "]";
        }

        private static String setCmd(String selector, String data) {
            return "{\"type\":\"Set\",\"selector\":" + str(selector) + ",\"data\":" + data + "}";
        }

        private static String message(Txt t) {
            if (t.key() == null) return "{\"RawText\":" + str(t.raw() == null ? "" : t.raw()) + "}";
            StringBuilder params = new StringBuilder(), nested = new StringBuilder();
            for (Map.Entry<String, Object> p : t.params().entrySet()) {
                if (p.getValue() instanceof Txt inner) {
                    nested.append(nested.isEmpty() ? "" : ",").append(str(p.getKey())).append(":").append(message(inner));
                } else {
                    params.append(params.isEmpty() ? "" : ",").append(str(p.getKey())).append(":").append(str(String.valueOf(p.getValue())));
                }
            }
            return "{\"MessageId\":" + str(t.key())
                + (params.isEmpty() ? "" : ",\"Params\":{" + params + "}")
                + (nested.isEmpty() ? "" : ",\"MessageParams\":{" + nested + "}") + "}";
        }

        private static String str(String s) {
            StringBuilder b = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    default -> {
                        if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                        else b.append(c);
                    }
                }
            }
            return b.append('"').toString();
        }
    }
}
