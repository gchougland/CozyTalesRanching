package com.hexvane.cozytalesranching.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.houseui.Txt;
import com.hexvane.cozytalesranching.houseui.UiOut;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Rancher's Ledger page: what {@link LedgerView} works out from the records and the commands {@link LedgerLayout}
 * sends. {@code tests/hytale/items-fix4.test.json} renders exactly these commands; run with
 * {@code ITEMS_FIX4_WRITE_CASES=true} to rewrite it after a layout or model change.
 */
class LedgerLayoutTest {
    static final UUID ME = UUID.fromString("11111111-2222-4333-8444-555555555555");
    static final Path TEST_FILE = Path.of("tests/hytale/items-fix4.test.json");
    static final String PROJECT = "_worktrees/CozyTales-Ranching-fix4-items";
    static final String RES = PROJECT + "/src/main/resources";

    @TempDir static Path data;

    @BeforeAll
    static void loadSpecies() {
        SpeciesRegistry.load(data);
    }

    // ---------- scenarios ----------
    record Ranch(List<HouseRecord> houses, List<AnimalRecord> animals, Set<UUID> inside, Map<UUID, Map<String, Integer>> stored) {
        LedgerView view() {
            return LedgerView.of(houses, animals, inside::contains, id -> stored.getOrDefault(id, Map.of()),
                id -> SpeciesRegistry.byId(id).orElse(null), h -> houseNameKey(h.blockId), "default");
        }
    }

    static Ranch typical() {
        List<HouseRecord> houses = new ArrayList<>();
        List<AnimalRecord> animals = new ArrayList<>();
        Set<UUID> inside = new java.util.HashSet<>();
        Map<UUID, Map<String, Integer>> stored = new HashMap<>();
        HouseRecord coop = house("chicken", "CozyRanch_House_ChickenCoop", "default", 120, -45, 6, 1);
        coop.name = "Sunny Roost";
        houses.add(coop);
        AnimalRecord a = animal("chicken", "Clucky", 5, false, coop, 1);
        AnimalRecord b = animal("chicken", "Henrietta", 3, false, coop, 2);
        AnimalRecord c = animal("chicken", null, 1, true, coop, 3);
        animals.addAll(List.of(a, b, c));
        inside.add(b.animalId);
        stored.put(coop.houseId, map("Food_Egg", 7, "Ingredient_Feathers_Light", 2));
        HouseRecord barn = house("cow", "CozyRanch_House_CowBarn", "default", 98, -30, 4, 2);
        houses.add(barn);
        AnimalRecord d = animal("cow", "Daisy", 4, false, barn, 4);
        d.state = AnimalRecord.State.MISSING;
        animals.add(d);
        animals.add(animal("cow", "Buttercup", 2, false, barn, 5));
        stored.put(barn.houseId, map("*Container_Bucket_State_Filled_Milk", 2));
        houses.add(house("frog", "CozyRanch_House_FrogPond", "Swamp_Getaway", 1500, 820, 4, 3));
        AnimalRecord follower = animal("pig", "Truffles", 2, false, null, 6);
        follower.following = true;
        animals.add(follower);
        animals.add(animal("sheep", "Woolly", 1, false, null, 7));
        return new Ranch(houses, animals, inside, stored);
    }

    static Ranch empty() {
        return new Ranch(List.of(), List.of(), Set.of(), Map.of());
    }

    /** Long names, eight kinds of product, and more animals with no house than the page lists. */
    static Ranch big() {
        List<HouseRecord> houses = new ArrayList<>();
        List<AnimalRecord> animals = new ArrayList<>();
        HouseRecord coop = house("chicken", "CozyRanch_House_ChickenCoop", "default", -2048, 4096, 6, 1);
        coop.name = "The Grand Featherbottom Estate";
        houses.add(coop);
        for (int i = 0; i < 6; i++) animals.add(animal("chicken", i == 0 ? "Princess Featherbottom IV" : "Hen " + (i + 1), i % 6, i == 5, coop, i));
        Map<String, Integer> lots = map("Food_Egg", 999, "Ingredient_Feathers_Light", 64, "CozyRanch_Truffle", 12, "Ingredient_Fabric_Scrap_Wool", 30);
        lots.putAll(map("CozyRanch_Duck_Egg", 5, "CozyRanch_Skrill_Egg", 3, "CozyRanch_Wild_Honey", 8, "CozyRanch_Rabbit_Fluff", 4));
        Map<UUID, Map<String, Integer>> stored = Map.of(coop.houseId, lots);
        for (int i = 0; i < 34; i++) {
            AnimalRecord r = animal(i % 2 == 0 ? "rabbit" : "duck", "Wanderer " + (i + 1), i % 5, false, null, 100 + i);
            r.following = i % 3 == 0;
            animals.add(r);
        }
        return new Ranch(houses, animals, Set.of(), stored);
    }

    // ---------- tests ----------
    @Test
    void housesListTheirResidentsAndWhereEachIs() {
        LedgerView v = typical().view();
        assertEquals(7, v.animals());
        assertEquals(3, v.houses());
        assertEquals(11, v.waiting());
        LedgerView.House coop = v.houseList().stream().filter(h -> "Sunny Roost".equals(h.name().raw())).findFirst().orElseThrow();
        assertEquals(3, coop.residents());
        assertEquals(List.of(LedgerView.Where.NEAR_HOME, LedgerView.Where.INSIDE, LedgerView.Where.NEAR_HOME),
            coop.animals().stream().map(LedgerView.Animal::where).toList(), "adults first, then the chick");
        assertTrue(coop.animals().get(2).baby());
        LedgerView.House barn = v.houseList().stream().filter(h -> h.species().key().equals("cozyranch_species.cow.name")).findFirst().orElseThrow();
        assertEquals(LedgerView.Where.LOST, barn.animals().getFirst().where());
        LedgerView.House pond = v.houseList().stream().filter(h -> h.residents() == 0).findFirst().orElseThrow();
        assertEquals("cozyranch_items.ledger.house.at.world", pond.where().key());
        assertEquals(List.of(LedgerView.Where.FOLLOWING, LedgerView.Where.STAYING), v.loose().stream().map(LedgerView.Animal::where).toList());
    }

    @Test
    void unnamedAnimalsUseTheirSpeciesName() {
        LedgerView.Animal chick = typical().view().houseList().stream().flatMap(h -> h.animals().stream()).filter(LedgerView.Animal::baby).findFirst().orElseThrow();
        assertEquals("cozyranch_species.chicken.name", chick.name().key());
    }

    @Test
    void longListsAreCut() {
        LedgerView v = big().view();
        assertEquals(LedgerView.MAX_LOOSE, v.loose().size());
        assertEquals(4, v.looseMore());
        Recorder ui = render(v);
        assertEquals(LedgerLayout.MAX_CHIPS - 1, ui.count("\"text\":\"" + LedgerLayout.CHIP + "\""));
        assertTrue(ui.has("#LedgerList[0] #LHMore.Visible", "true"));
        assertTrue(ui.has("#LedgerList[0] #LHMore.TextSpans", "{\"MessageId\":\"cozyranch_items.ledger.products.more\",\"Params\":{\"count\":\"3\"}}"));
    }

    @Test
    void emptyRanchShowsTheInvitation() {
        Recorder ui = render(empty().view());
        assertTrue(ui.has("#LedgerEmpty.Visible", "true"));
        assertTrue(ui.has("#LedgerList.Visible", "false"));
        assertEquals(0, ui.count("LedgerHouse.ui"));
    }

    @Test
    void rowsFollowTheirHouseAndHeartsMatch() {
        Recorder ui = render(typical().view());
        // the chicken coop card, its three hens, then the cow barn card
        assertTrue(ui.commands.get(ui.commands.indexOf("{\"type\":\"Append\",\"selector\":\"#LedgerList\",\"text\":\"CozyRanch/LedgerHouse.ui\"}") + 1)
            .contains("#LedgerList[0] #LHName"));
        assertTrue(ui.has("#LedgerList[1] #LAHeart5.Visible", "true"));
        assertTrue(ui.has("#LedgerList[2] #LAHeart4.Visible", "false"));
        assertTrue(ui.has("#LedgerList[3] #LABaby.Visible", "true"));
        assertTrue(ui.has("#LedgerList[2] #LAWhere.TextSpans", "{\"MessageId\":\"cozyranch_items.ledger.where.inside\"}"));
    }

    @Test
    void everyLedgerKeyIsInTheLangFile() throws IOException {
        String lang = Files.readString(Path.of("src/main/resources/Server/Languages/en-US/cozyranch_items.lang"), StandardCharsets.UTF_8);
        List<String> keys = new ArrayList<>(List.of("hint", "title", "summary", "button.journal", "empty", "denied", "house.count", "house.line",
            "house.at", "house.at.world", "house.empty", "products.none", "products.count", "products.more", "loose.title", "loose.more", "baby"));
        for (LedgerView.Where w : LedgerView.Where.values()) keys.add("where." + w.name().toLowerCase(java.util.Locale.ROOT));
        for (String k : keys) assertTrue(Pattern.compile("(?m)^ledger\\." + Pattern.quote(k) + "=").matcher(lang).find(), "no lang key ledger." + k);
        assertTrue(lang.contains("ledger.hint=Press [{key}]"), "the hint names the key");
    }

    @Test
    void testFileRendersWhatTheJavaSends() throws IOException {
        String generated = testFile();
        if ("true".equals(System.getenv("ITEMS_FIX4_WRITE_CASES"))) {
            Files.writeString(TEST_FILE, generated, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(TEST_FILE), "run with ITEMS_FIX4_WRITE_CASES=true to create " + TEST_FILE);
        String committed = Files.readString(TEST_FILE, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?m)^\\s*\"commands\": (\\[.*]),?$").matcher(generated);
        int found = 0;
        while (m.find()) {
            found++;
            assertTrue(committed.contains(m.group(1)), "items-fix4.test.json is out of date with LedgerLayout; rerun with ITEMS_FIX4_WRITE_CASES=true");
        }
        assertEquals(3, found);
    }

    // ---------- the test file ----------
    static String testFile() {
        StringBuilder out = new StringBuilder("{\n  \"cases\": [\n");
        out.append("    {\n      \"name\": \"items-fix4 gate\",\n      \"description\": \"The pack validates and the ledger page's Java names only ids and properties that exist.\",\n      \"steps\": [\n");
        out.append("        { \"tool\": \"asset_validate\", \"arguments\": { \"project\": \"" + PROJECT + "\" }, \"expect\": { \"valid\": true, \"schemaErrors\": 0 } },\n");
        out.append("        { \"tool\": \"ui_check_java\", \"arguments\": { \"project\": \"" + PROJECT + "\" }, \"expect\": { \"valid\": true } },\n");
        out.append("        { \"tool\": \"asset_check\", \"name\": \"CozyRanch_Ranch_Ledger\", \"arguments\": { \"root\": \"projects\", \"path\": \"" + RES
            + "/Server/Item/Items/CozyRanch/Decor/CozyRanch_Ranch_Ledger.json\" }, \"expect\": { \"errors\": 0 } }\n");
        out.append("      ]\n    },\n");
        out.append("    {\n      \"name\": \"ledger page\",\n      \"description\": \"The Rancher's Ledger page with the commands LedgerLayout sends (generated by LedgerLayoutTest).\",\n      \"steps\": [\n");
        String[][] renders = {{"typical", "three houses, products waiting, a lost cow, a pond in another world, two animals with no house"},
            {"empty", "nothing tamed yet"}, {"big", "long names, eight kinds of product, more animals with no house than fit"}};
        for (int i = 0; i < renders.length; i++) {
            Ranch r = switch (renders[i][0]) {
                case "typical" -> typical();
                case "empty" -> empty();
                default -> big();
            };
            out.append("        {\n          \"tool\": \"ui_render\",\n          \"name\": \"" + renders[i][1] + "\",\n");
            out.append("          \"arguments\": {\n            \"root\": \"projects\",\n            \"path\": \"" + RES + "/Common/UI/Custom/CozyRanch/LedgerPage.ui\",\n");
            out.append("            \"commands\": " + render(r.view()).json() + "\n          },\n");
            out.append("          \"expect\": { \"valid\": true, \"errors\": 0, \"notes\": { \"$length\": 0 } },\n");
            out.append("          \"golden\": \"tests/hytale/golden/fix4-items/ledger-" + renders[i][0] + ".png\"\n        }" + (i + 1 < renders.length ? "," : "") + "\n");
        }
        out.append("      ]\n    },\n");
        out.append("    {\n      \"name\": \"eggs and rug\",\n      \"description\": \"The eggs on vanilla Food_Egg's one-box shape, the blocky fried egg and the flat Spotted Rug, with their item JSON.\",\n      \"steps\": [\n");
        String[][] models = {
            {"Produce/CozyRanch_Skrill_Egg", "0.3", "0.35"}, {"Produce/CozyRanch_Duck_Egg", "0.3", "0.35"},
            {"Cooking/CozyRanch_Roasted_Skrill_Egg", "0.3", "0.35"}, {"Cooking/CozyRanch_Fried_Duck_Egg", "0.3", "0.1"},
            {"Decor/CozyRanch_Spotted_Rug", "1.9", "0.07"}};
        for (int i = 0; i < models.length; i++) {
            String[] m = models[i];
            String name = m[0].substring(m[0].indexOf('/') + 1);
            out.append("        { \"tool\": \"asset_check\", \"name\": \"" + name + " json\", \"arguments\": { \"root\": \"projects\", \"path\": \"" + RES
                + "/Server/Item/Items/CozyRanch/" + m[0] + ".json\" }, \"expect\": { \"errors\": 0 } },\n");
            out.append("        { \"tool\": \"texture_check\", \"name\": \"" + name + " texture\", \"arguments\": { \"root\": \"projects\", \"image\": false, \"path\": \"" + RES
                + "/Server/Item/Items/CozyRanch/" + m[0] + ".json\" }, \"expect\": { \"verdict\": { \"$not\": \"FAIL\" } } },\n");
            out.append("        {\n          \"tool\": \"model_render\",\n          \"name\": \"" + name + "\",\n          \"arguments\": { \"root\": \"projects\", \"path\": \"" + RES
                + "/Server/Item/Items/CozyRanch/" + m[0] + ".json\", \"views\": [\"three_quarter\", \"front\", \"top\"], \"size\": 240, \"hitbox\": false },\n");
            out.append("          \"expect\": { \"notes\": { \"$length\": 0 }, \"shapes\": { \"$lte\": 3 }, \"sizeBlocks\": { \"width\": { \"$lte\": " + m[1]
                + " }, \"height\": { \"$lte\": " + m[2] + " } } },\n");
            out.append("          \"golden\": \"tests/hytale/golden/fix4-items/" + name + ".png\"\n        }" + (i + 1 < models.length ? "," : "") + "\n");
        }
        out.append("      ]\n    }\n  ]\n}\n");
        return out.toString();
    }

    // ---------- helpers ----------
    static Recorder render(LedgerView view) {
        Recorder ui = new Recorder();
        LedgerLayout.render(view, ui);
        return ui;
    }

    static HouseRecord house(String species, String block, String world, int x, int z, int capacity, long placed) {
        HouseRecord h = new HouseRecord();
        h.houseId = new UUID(1, placed);
        h.ownerId = ME;
        h.speciesId = species;
        h.blockId = block;
        h.worldName = world;
        h.x = x;
        h.y = 64;
        h.z = z;
        h.capacity = capacity;
        h.placedAtMs = placed;
        return h;
    }

    static AnimalRecord animal(String species, String name, int hearts, boolean baby, HouseRecord house, long order) {
        AnimalRecord r = new AnimalRecord();
        r.animalId = new UUID(2, order);
        r.ownerId = ME;
        r.speciesId = species;
        r.name = name;
        r.hearts = hearts;
        r.baby = baby;
        r.houseId = house == null ? null : house.houseId;
        r.worldName = house == null ? "default" : house.worldName;
        r.tamedAtMs = 1_000L * order;
        return r;
    }

    static Map<String, Integer> map(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
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
