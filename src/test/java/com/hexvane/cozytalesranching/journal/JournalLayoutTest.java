package com.hexvane.cozytalesranching.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.breeding.TraitRegistry;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Renders the journal's pages for each {@link JournalDemo} scenario through {@link JournalLayout} and checks that
 * {@code tests/hytale/journal.test.json} renders exactly those commands. Run with {@code JOURNAL_WRITE_CASES=true} to
 * rewrite the test file after a layout change.
 */
class JournalLayoutTest {
    static final UUID PLAYER = UUID.fromString("11111111-2222-4333-8444-555555555555");
    static final Path TEST_FILE = Path.of("tests/hytale/journal.test.json");
    static final String PROJECT = "CozyTales-Ranching";
    static final Path RES = Path.of("src/main/resources");
    /** Vanilla state items name themselves through their state's TranslationProperties, which only the asset map knows. */
    static final Map<String, String> VANILLA_STATE_NAMES = Map.of(
        "*Container_Bucket_State_Filled_Milk", "server.items.Container_Bucket_Milk.name");

    /** page, scenario, species or spread, description, golden name */
    static final String[][] RENDERS = {
        {"contents", "EMPTY", "", "contents page, nothing found yet", "journal-contents-empty"},
        {"contents", "PARTIAL", "", "contents page, cow and chicken found", "journal-contents-partial"},
        {"contents", "FULL", "", "contents page, every animal found", "journal-contents-full"},
        {"species", "FULL", "cow", "cow page, everything discovered, all nine traits", "journal-species-full"},
        {"species", "PARTIAL", "cow", "cow page, befriended and housed, two habits and one trait seen", "journal-species-partial"},
        {"species", "PARTIAL", "chicken", "chicken page, fed once", "journal-species-fed"},
        {"species", "EMPTY", "cow", "cow page, not met yet", "journal-species-undiscovered"},
        {"species", "FULL", "turkey", "turkey page, everything discovered: what counts as a perch", "journal-species-counts"},
        {"traits", "EMPTY", "0", "traits page, no trait seen", "journal-traits-empty"},
        {"traits", "PARTIAL", "0", "traits page, one trait seen", "journal-traits-partial"},
        {"traits", "FULL", "0", "traits page, every trait seen", "journal-traits-full"},
        {"memories", "EMPTY", "0", "memories page, no memories", "journal-memories-empty"},
        {"memories", "FULL", "0", "memories page, every memory, first spread", "journal-memories-full"},
        {"memories", "FULL", "1", "memories page, every memory, last spread", "journal-memories-full-2"},
        {"guide", "EMPTY", "", "how to page", "journal-guide"},
        {"settings", "EMPTY", "on", "settings page, messages and sounds on", "journal-settings-on"},
        {"settings", "EMPTY", "mixed", "settings page, messages off, sounds on", "journal-settings-mixed"},
    };

    @TempDir static Path data;
    static JournalBook book;
    static JournalView.Context context;

    @BeforeAll
    static void load() throws IOException {
        SpeciesRegistry.load(data);
        TraitRegistry.load(data);
        try (var in = Files.newInputStream(RES.resolve("Server/CozyTalesRanching/Journal.json"))) {
            book = JournalBook.read(com.hexvane.cozytalesranching.species.SpeciesOverlay.merged(in, "Journal"));
        }
        context = new JournalView.Context(book, book.ordered(), TraitRegistry.get(), new TestLookup());
    }

    @Test
    void contentsShowsSilhouettesUntilMet() {
        Recorder ui = render("contents", "PARTIAL", "");
        assertEquals(SpeciesRegistry.all().size(), ui.count("\"selector\":\"#Cards\",\"text\":\"CozyRanch/JournalCard.ui\""));
        assertTrue(ui.has("#Cards[0] #CardArt.AssetPath", "\"UI/Custom/CozyRanch/Journal/Cow.png\""));
        assertTrue(ui.has("#Cards[2] #CardArt.AssetPath", "\"UI/Custom/CozyRanch/Journal/PigSilhouette.png\""));
        assertTrue(ui.has("#Cards[2] #CardName.TextSpans", "{\"MessageId\":\"cozyranch_journal.unknown\"}"));
    }

    @Test
    void undiscoveredPageHidesEverything() {
        JournalView.SpeciesPage p = JournalView.species(new PlayerJournal(), context, species("cow"));
        assertFalse(p.known());
        assertEquals("cozyranch_journal.unknown", p.name().key());
        for (JournalView.Section s : concat(p.left(), p.right())) {
            assertEquals(1, s.lines().size());
            assertTrue(s.lines().getFirst().text().key().startsWith("cozyranch_journal.hint."), s.title().key());
        }
    }

    @Test
    void fedOncePageShowsTheDietOnly() {
        PlayerJournal j = demo("PARTIAL");
        JournalView.SpeciesPage p = JournalView.species(j, context, species("chicken"));
        assertTrue(p.known());
        assertEquals("cozyranch_journal.section.diet", p.left().get(0).title().key());
        assertEquals("cozyranch_journal.diet.eats", p.left().get(0).lines().get(0).text().key());
        assertEquals("cozyranch_journal.hint.treats", p.left().get(1).lines().get(0).text().key());
        assertEquals("cozyranch_journal.hint.care", p.right().get(0).lines().get(0).text().key());
    }

    @Test
    void fullPageListsEverySeenBehaviourAndTheRareLine() {
        JournalView.SpeciesPage p = JournalView.species(demo("FULL"), context, species("cow"));
        JournalView.Section habits = p.right().stream().filter(s -> "cozyranch_journal.section.behaviours".equals(s.title().key())).findFirst().orElseThrow();
        assertEquals(book.page("cow").Behaviours.size(), habits.lines().size());
        assertEquals("cozyranch_journal.section.rare", p.right().getLast().title().key());
    }

    @Test
    void speciesPageNamesAndDescribesEachSeenTrait() {
        JournalView.SpeciesPage p = JournalView.species(demo("FULL"), context, species("cow"));
        JournalView.Section traits = section(p, "traits");
        List<String> keys = traits.lines().stream().map(l -> l.text().key()).toList();
        for (var t : TraitRegistry.get().Traits) {
            int at = keys.indexOf(t.NameKey);
            assertTrue(at >= 0, t.Id);
            assertEquals(JournalView.Style.STRONG, traits.lines().get(at).style());
            assertEquals(t.DescKey, keys.get(at + 1), t.Id);
        }
        JournalView.SpeciesPage partial = JournalView.species(demo("PARTIAL"), context, species("cow"));
        JournalView.Section one = section(partial, "traits");
        assertEquals(3, one.lines().size(), "one trait: name, description, how many more");
    }

    @Test
    void traitsPageHidesUnseenTraitsAndCountsSeenOnes() {
        int total = TraitRegistry.get().Traits.size();
        JournalView.Traits empty = JournalView.traits(new PlayerJournal(), context, 0);
        assertEquals(Math.min(total, JournalView.TRAITS_PER_SPREAD), empty.left().size() + empty.right().size());
        assertTrue(concat(empty.left(), empty.right()).stream().noneMatch(JournalView.TraitRow::known));
        assertEquals("0", String.valueOf(empty.progress().params().get("found")));

        JournalView.Traits one = JournalView.traits(demo("PARTIAL"), context, 0);
        assertEquals(1, concat(one.left(), one.right()).stream().filter(JournalView.TraitRow::known).count());

        JournalView.Traits full = JournalView.traits(demo("FULL"), context, 0);
        assertTrue(concat(full.left(), full.right()).stream().allMatch(JournalView.TraitRow::known));
        assertEquals(String.valueOf(total), String.valueOf(full.progress().params().get("found")));
        assertEquals(TraitRegistry.get().Traits.getFirst().DescKey, full.left().getFirst().text().key());
    }

    @Test
    void memoriesArePaged() {
        PlayerJournal j = demo("FULL");
        assertTrue(j.Memories.size() > JournalView.MEMORIES_PER_SPREAD, "the full demo should overflow one spread");
        JournalView.Memories first = JournalView.memories(j, context, 0);
        assertEquals(JournalView.MEMORIES_PER_PAGE, first.left().size());
        assertEquals(JournalView.MEMORIES_PER_PAGE, first.right().size());
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());
        JournalView.Memories last = JournalView.memories(j, context, 99);
        assertEquals(first.spreads() - 1, last.spread());
        assertFalse(last.hasNext());
    }

    @Test
    void speciesNextAndPreviousWrapAround() {
        List<SpeciesDefinition> all = context.species();
        JournalView.SpeciesPage firstPage = JournalView.species(new PlayerJournal(), context, all.getFirst());
        assertEquals(all.getLast().Id, firstPage.previous());
        assertEquals(all.get(1).Id, firstPage.next());
    }

    @Test
    void settingsShowEachToggleAndSendItsId() {
        Recorder on = render("settings", "EMPTY", "on");
        assertTrue(on.has("#ChatToggle.TextSpans", "{\"MessageId\":\"cozyranch_journal.settings.on\"}"));
        assertTrue(on.has("#SoundsToggle.TextSpans", "{\"MessageId\":\"cozyranch_journal.settings.on\"}"));
        Recorder mixed = render("settings", "EMPTY", "mixed");
        assertTrue(mixed.has("#ChatToggle.TextSpans", "{\"MessageId\":\"cozyranch_journal.settings.off\"}"));
        assertTrue(mixed.has("#SoundsToggle.TextSpans", "{\"MessageId\":\"cozyranch_journal.settings.on\"}"));
        assertEquals(List.of("chat", "sounds"), JournalView.settings(true, true).stream().map(JournalView.SettingRow::id).toList());
        for (JournalView.SettingRow r : JournalView.settings(true, false)) {
            assertEquals(r.id().equals("chat"), r.on());
            assertTrue(com.hexvane.cozytalesranching.prefs.PlayerPrefs.Setting.byId(r.id()) != null, r.id());
        }
    }

    @Test
    void speciesPageSaysWhatCountsForEachHabitatFeature() {
        Recorder turkey = render("species", "FULL", "turkey");
        String json = turkey.json();
        assertTrue(json.contains("\"MessageId\":\"cozyranch_journal.section.counts\""), "a What counts section");
        assertTrue(json.contains("{\"MessageId\":\"cozyranch_journal.counts.line\",\"MessageParams\":{\"name\":{\"MessageId\":\"cozyranch_houseui.habitat.perch\"},"
            + "\"counts\":{\"MessageId\":\"cozyranch_houseui.counts.yard.many\",\"Params\":{\"count\":\"2\"}"), json);
        assertFalse(json.contains("counts.unknown"), "every feature the turkey names is defined");
        assertFalse(render("species", "EMPTY", "cow").json().contains("section.counts"), "nothing until the home is known");
    }

    @Test
    void guideIsShortAndPlain() throws IOException {
        Set<String> keys = langKeys();
        JournalView.Guide g = JournalView.guide();
        Map<String, String> text = langText();
        int lines = 0;
        for (JournalView.Section s : concat(g.left(), g.right())) {
            assertTrue(keys.contains(s.title().key()), s.title().key());
            for (JournalView.Line l : s.lines()) {
                lines++;
                String t = text.get(l.text().key());
                assertTrue(t != null, l.text().key());
                assertTrue(t.length() <= 90, "short: " + t);
                assertFalse(t.contains(" - ") || t.contains("—") || t.contains("–"), "no dashes: " + t);
            }
        }
        assertTrue(lines <= 20, "a short page: " + lines + " lines");
        String all = String.join(" ", text.values());
        for (String must : new String[] {"Crouch", "name", "Ranching Bench", "trough", "decorations", "Show area", "once a day", "baby", "Collect", "Jar of Crickets"}) {
            assertTrue(concat(g.left(), g.right()).stream().flatMap(s -> s.lines().stream()).anyMatch(l -> text.get(l.text().key()).contains(must)), "the guide mentions " + must);
        }
        assertFalse(all.isEmpty());
    }

    static Map<String, String> langText() throws IOException {
        Map<String, String> out = new java.util.HashMap<>();
        for (String line : Files.readAllLines(RES.resolve("Server/Languages/en-US/cozyranch_journal.lang"), StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq > 0 && !line.startsWith("#")) out.put("cozyranch_journal." + line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return out;
    }

    @Test
    void everyKeyTheJournalSendsExists() throws IOException {
        Set<String> keys = langKeys();
        Set<String> sent = new HashSet<>();
        for (String[] r : RENDERS) collectKeys(render(r[0], r[1], r[2]).json(), sent);
        for (String key : sent) {
            if (key.startsWith("server.items.")) continue;
            assertTrue(keys.contains(key), "no en-US string for " + key);
        }
    }

    @Test
    void everyListedBehaviourHasANameAndANote() throws IOException {
        Set<String> keys = langKeys();
        for (JournalBook.SpeciesPage p : book.Species) {
            for (String b : p.Behaviours) {
                assertTrue(keys.contains(JournalView.behaviourNote(b).key()), b);
                assertTrue(keys.contains(JournalView.behaviourName(b).key()), b);
            }
            for (String path : new String[] {p.Art, p.Silhouette}) {
                String file = path.replaceFirst("\\.png$", "@2x.png");
                assertTrue(Files.isRegularFile(RES.resolve("Common").resolve(file)), path);
            }
        }
    }

    @Test
    void testFileRendersWhatTheJavaSends() throws IOException {
        String generated = testFile();
        if ("true".equals(System.getenv("JOURNAL_WRITE_CASES"))) {
            Files.createDirectories(TEST_FILE.getParent());
            Files.writeString(TEST_FILE, generated, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(TEST_FILE), "run with JOURNAL_WRITE_CASES=true to create " + TEST_FILE);
        String committed = Files.readString(TEST_FILE, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?m)^\\s*\"commands\": (\\[.*]),?$").matcher(generated);
        int found = 0;
        while (m.find()) {
            found++;
            assertTrue(committed.contains(m.group(1)), "journal.test.json is out of date with JournalLayout; rerun with JOURNAL_WRITE_CASES=true");
        }
        assertEquals(RENDERS.length, found);
    }

    // ---- helpers ----

    static PlayerJournal demo(String scenario) {
        return JournalDemo.create(JournalDemo.Scenario.valueOf(scenario), PLAYER, book, context.species(), TraitRegistry.get().ids());
    }

    static SpeciesDefinition species(String id) {
        return SpeciesRegistry.byId(id).orElseThrow();
    }

    static Recorder render(String page, String scenario, String arg) {
        PlayerJournal j = demo(scenario);
        Recorder ui = new Recorder();
        switch (page) {
            case "contents" -> JournalLayout.contents(JournalView.contents(j, context, "Hexvane"), ui);
            case "species" -> JournalLayout.species(JournalView.species(j, context, species(arg)), ui);
            case "traits" -> JournalLayout.traits(JournalView.traits(j, context, Integer.parseInt(arg)), ui);
            case "guide" -> JournalLayout.guide(JournalView.guide(), ui);
            case "settings" -> JournalLayout.settings(JournalView.settings(!arg.equals("mixed"), true), ui);
            default -> JournalLayout.memories(JournalView.memories(j, context, Integer.parseInt(arg)), ui);
        }
        return ui;
    }

    static String testFile() {
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"cases\": [\n");
        out.append("    {\n      \"name\": \"journal gate\",\n      \"description\": \"Every UI document of the pack parses, and the journal's Java names only ids and properties that exist.\",\n      \"steps\": [\n");
        out.append("        { \"tool\": \"asset_validate\", \"arguments\": { \"project\": \"" + PROJECT + "\" }, \"expect\": { \"valid\": true, \"schemaErrors\": 0 } },\n");
        out.append("        { \"tool\": \"ui_check_java\", \"arguments\": { \"project\": \"" + PROJECT + "\" }, \"expect\": { \"valid\": true } }\n");
        out.append("      ]\n    },\n");
        out.append("    {\n      \"name\": \"journal pages\",\n      \"description\": \"The journal's pages for each /ranch journal demo state, with the commands JournalLayout sends (generated by JournalLayoutTest).\",\n      \"steps\": [\n");
        for (int i = 0; i < RENDERS.length; i++) {
            String[] r = RENDERS[i];
            String doc = switch (r[0]) {
                case "contents" -> JournalLayout.CONTENTS;
                case "species" -> JournalLayout.SPECIES;
                case "traits" -> JournalLayout.TRAITS;
                case "guide" -> JournalLayout.GUIDE;
                case "settings" -> JournalLayout.SETTINGS;
                default -> JournalLayout.MEMORIES;
            };
            out.append("        {\n");
            out.append("          \"tool\": \"ui_render\",\n");
            out.append("          \"name\": \"" + r[3] + "\",\n");
            out.append("          \"arguments\": {\n");
            out.append("            \"root\": \"projects\",\n");
            out.append("            \"path\": \"" + PROJECT + "/src/main/resources/Common/UI/Custom/" + doc + "\",\n");
            out.append("            \"commands\": " + render(r[0], r[1], r[2]).json() + "\n");
            out.append("          },\n");
            out.append("          \"expect\": { \"valid\": true, \"errors\": 0, \"notes\": { \"$length\": 0 } },\n");
            out.append("          \"golden\": \"tests/hytale/golden/" + r[4] + ".png\"\n");
            out.append("        }" + (i + 1 < RENDERS.length ? "," : "") + "\n");
        }
        out.append("      ]\n    }\n  ]\n}\n");
        return out.toString();
    }

    static Set<String> langKeys() throws IOException {
        Set<String> keys = new HashSet<>();
        try (Stream<Path> files = Files.list(RES.resolve("Server/Languages/en-US"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".lang")).toList()) {
                String prefix = f.getFileName().toString().replaceFirst("\\.lang$", "");
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    Matcher m = Pattern.compile("^\\s*([^#=\\s][^=]*?)\\s*=").matcher(line.replace("\uFEFF", ""));
                    if (m.find()) keys.add(prefix + "." + m.group(1));
                }
            }
        }
        return keys;
    }

    static void collectKeys(String json, Set<String> out) {
        Matcher m = Pattern.compile("\"MessageId\":\"([^\"]+)\"").matcher(json);
        while (m.find()) out.add(m.group(1));
    }

    static JournalView.Section section(JournalView.SpeciesPage p, String id) {
        return p.right().stream().filter(s -> ("cozyranch_journal.section." + id).equals(s.title().key())).findFirst().orElseThrow();
    }

    static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    /**
     * Item names as the server gives them: the mod's TranslationProperties name, else vanilla's convention
     * ({@code Plant_Crop_Wheat_Item} is named {@code server.items.Plant_Crop_Wheat.name}). {@code Drop_*} ids are drop
     * lists, not items.
     */
    static final class TestLookup implements JournalView.Lookup {
        static final com.hexvane.cozytalesranching.care.HabitatConfig HABITAT = com.hexvane.cozytalesranching.houseui.HabitatCountsTest.config();
        private final Set<String> keys;

        TestLookup() {
            try {
                keys = langKeys();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String itemNameKey(String itemId) {
            String state = VANILLA_STATE_NAMES.get(itemId);
            if (state != null) return state;
            try (Stream<Path> files = Files.walk(RES.resolve("Server/Item/Items"))) {
                Path f = files.filter(p -> p.getFileName().toString().equals(itemId + ".json")).findFirst().orElse(null);
                if (f != null) {
                    Matcher m = Pattern.compile("\"Name\"\\s*:\\s*\"([^\"]+)\"").matcher(Files.readString(f, StandardCharsets.UTF_8));
                    if (m.find()) return m.group(1);
                }
            } catch (IOException ignored) {
                // vanilla key below
            }
            if (itemId.startsWith("Drop_")) return null;
            return "server.items." + itemId.replaceFirst("_Item$", "") + ".name";
        }

        @Override public boolean hasKey(String key) { return keys.contains(key); }

        @Override public com.hexvane.cozytalesranching.care.HabitatConfig.Feature habitatFeature(String featureId) { return HABITAT.feature(featureId); }

        @Override public int breedingMinHearts() { return RanchConfig.get().breedingMinHearts(); }

        @Override public ZoneId zone() { return ZoneOffset.UTC; }
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

        @Override public void click(String selector, String action, String arg) {}

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
