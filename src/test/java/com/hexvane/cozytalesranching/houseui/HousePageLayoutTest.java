package com.hexvane.cozytalesranching.houseui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    static final String PROJECT = "CozyTales-Ranching";
    static final String[][] RENDERS = {
        {"EMPTY", "empty pigsty", "houseui-empty"},
        {"TYPICAL", "typical cow barn: three residents, one inside, one follower", "houseui-typical"},
        {"FULL", "full chicken coop: six residents, long names, a baby, traits", "houseui-full"},
        {"UNCHECKED", "frog pond whose habitat has not been checked", "houseui-unchecked"},
        {"MISSING", "frog pond missing lily pads (needed) and reeds (liked)", "houseui-missing"},
        {"BREEDINGOFF", "sheepfold with breeding off", "houseui-breedingoff"},
        {"TYPICAL", "a button pressed before the house service is installed", "houseui-unavailable", "result.unavailable"},
    };

    @TempDir static Path data;

    @BeforeAll
    static void loadSpecies() {
        SpeciesRegistry.load(data);
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
        HouseDemo demo = HouseDemo.create(scenario, PLAYER);
        HouseRecord house = demo.house();
        SpeciesDefinition species = SpeciesRegistry.byId(house.speciesId).orElseThrow();
        HouseView view = HouseView.of(house, species, houseNameKey(house.blockId), demo.residents(house), demo.service()::isInside,
            demo.followers(house, PLAYER), noticeKey == null ? null : Txt.ui(noticeKey));
        Recorder ui = new Recorder();
        HousePageLayout.render(view, ui);
        return ui;
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
            Recorder rec = render(HouseDemo.Scenario.valueOf(r[0]), r.length > 3 ? r[3] : null);
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

        @Override public void click(String selector, String action, String animal) {}

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
