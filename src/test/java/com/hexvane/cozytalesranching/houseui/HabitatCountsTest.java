package com.hexvane.cozytalesranching.houseui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.cozytalesranching.care.HabitatConfig;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesOverlay;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What counts for each habitat feature: built from Habitat.json and the species fragments, short, every key in en-US. */
public class HabitatCountsTest {
    static final Path RES = Path.of("src/main/resources");
    /** Vanilla item name keys (from the items' TranslationProperties) the habitat files name directly. */
    static final Map<String, String> VANILLA = Map.of(
        "Plant_Hay_Bundle", "server.items.Plant_Hay_Bundle.name",
        "Deco_Hive", "server.items.Deco_Hive.name",
        "Wood_Deadwood_Fence", "server.items.Wood_Deadwood_Fence.name",
        "Wood_Fir_Trunk", "server.items.Wood_Fir_Trunk.name");

    @TempDir static Path data;
    static HabitatConfig habitat;

    @BeforeAll
    static void load() throws IOException {
        SpeciesRegistry.load(data);
        habitat = config();
    }

    /** The habitat file with every species fragment merged in, as care reads it. */
    public static HabitatConfig config() {
        try (InputStream in = Files.newInputStream(RES.resolve("Server/CozyTalesRanching/Habitat.json"))) {
            return HabitatConfig.read(SpeciesOverlay.merged(in, "Habitat"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code Item.getTranslationKey} stand-in: our items' TranslationProperties name, else the vanilla ones above. */
    public static String itemNameKey(String itemId) {
        String vanilla = VANILLA.get(itemId);
        if (vanilla != null) return vanilla;
        try (Stream<Path> files = Files.walk(RES.resolve("Server/Item/Items"))) {
            Path f = files.filter(p -> p.getFileName().toString().equals(itemId + ".json")).findFirst().orElse(null);
            if (f == null) return null;
            Matcher m = Pattern.compile("\"Name\"\\s*:\\s*\"([^\"]+)\"").matcher(Files.readString(f, StandardCharsets.UTF_8));
            return m.find() ? m.group(1) : null;
        } catch (IOException e) {
            return null;
        }
    }

    static Txt explain(String id) {
        return HabitatCounts.explain(habitat.feature(id), HabitatCountsTest::itemNameKey);
    }

    @Test
    void perchNamesFencesHayAndTheToyLog() {
        Txt perch = explain("perch");
        assertEquals("cozyranch_houseui.counts.yard.many", perch.key());
        assertEquals(2, perch.params().get("count"));
        List<String> keys = keys((Txt) perch.params().get("things"));
        assertEquals(3, keys.size(), keys.toString());
        assertEquals("cozyranch_houseui.counts.group.fences", keys.get(0));
        assertTrue(keys.get(1).endsWith("CozyRanch_Toy_Log.name"), keys.get(1));
        assertEquals("server.items.Plant_Hay_Bundle.name", keys.get(2));
    }

    @Test
    void specialRulesHaveTheirOwnLines() {
        assertEquals("cozyranch_houseui.counts.food", explain("food").key());
        Txt shade = explain("shade");
        assertEquals("cozyranch_houseui.counts.shade", shade.key());
        assertEquals(6, shade.params().get("count"));
        Txt open = explain("open_space");
        assertEquals("cozyranch_houseui.counts.open", open.key());
        assertEquals(35, open.params().get("percent"));
        assertEquals("cozyranch_houseui.counts.surface.many", explain("mud").key(), "mud must be uncovered");
        assertEquals("cozyranch_houseui.counts.yard.one", explain("toy_ball").key());
        assertEquals("cozyranch_houseui.counts.unknown", HabitatCounts.explain(null, HabitatCountsTest::itemNameKey).key());
    }

    @Test
    void aFamilyMetThroughOneBlockIsCalledByThatBlock() {
        List<String> keys = keys((Txt) explain("woodpecker_deadwood").params().get("things"));
        assertTrue(keys.contains("cozyranch_houseui.counts.group.deadwood"), keys.toString());
        assertTrue(keys.contains("server.items.Wood_Deadwood_Fence.name"), "not every wooden fence counts: " + keys);
        assertEquals(List.of("cozyranch_houseui.counts.group.water"), keys((Txt) explain("water").params().get("things")));
    }

    @Test
    void longListsEndWithAndMore() {
        Txt things = (Txt) explain("forest_edge").params().get("things");
        assertTrue(things.key() != null);
        List<Txt> five = List.of(Txt.raw("a"), Txt.raw("b"), Txt.raw("c"), Txt.raw("d"), Txt.raw("e"));
        Txt list = HabitatCounts.list(five);
        assertEquals("cozyranch_houseui.counts.more", list.key());
        assertEquals(HabitatCounts.MAX_NAMES, keysAndRaw(list).size());
    }

    @Test
    void everyFeatureOfEverySpeciesIsExplainedWithKeysThatExist() throws IOException {
        Set<String> lang = langKeys();
        Set<String> ids = new HashSet<>(habitat.Features.keySet());
        for (SpeciesDefinition s : SpeciesRegistry.all()) {
            ids.addAll(s.HabitatRequires);
            ids.addAll(s.HabitatPrefers);
        }
        assertTrue(ids.contains("perch"));
        for (String id : ids) {
            Txt t = explain(id);
            assertNotNull(habitat.feature(id), "a species names habitat feature " + id + " that no habitat file defines");
            assertTrue(!t.key().endsWith("counts.unknown"), id);
            for (String key : allKeys(t)) {
                if (key.startsWith("server.items.")) continue;
                assertTrue(lang.contains(key), "no en-US string for " + key + " (" + id + ")");
            }
            assertTrue(rawTexts(t).isEmpty(), id + " fell back to a made-up name: " + rawTexts(t));
        }
    }

    static List<String> keys(Txt list) {
        List<String> out = new ArrayList<>();
        for (Txt t : flatten(list)) out.add(t.key());
        return out;
    }

    static List<Txt> keysAndRaw(Txt list) {
        return flatten(list);
    }

    /** The names inside a "{a}, {b}" list (and "{list} and more"), in order. */
    static List<Txt> flatten(Txt t) {
        List<Txt> out = new ArrayList<>();
        if (t.key() != null && (t.key().endsWith("counts.list") || t.key().endsWith("counts.more"))) {
            for (Object v : t.params().values()) out.addAll(flatten((Txt) v));
        } else {
            out.add(t);
        }
        return out;
    }

    static Set<String> allKeys(Txt t) {
        Set<String> out = new HashSet<>();
        if (t.key() != null) out.add(t.key());
        for (Object v : t.params().values()) if (v instanceof Txt inner) out.addAll(allKeys(inner));
        return out;
    }

    static List<String> rawTexts(Txt t) {
        List<String> out = new ArrayList<>();
        if (t.key() == null) out.add(t.raw());
        for (Object v : t.params().values()) if (v instanceof Txt inner) out.addAll(rawTexts(inner));
        return out;
    }

    static Set<String> langKeys() throws IOException {
        Set<String> keys = new HashSet<>();
        try (Stream<Path> files = Files.list(RES.resolve("Server/Languages/en-US"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".lang")).toList()) {
                String prefix = f.getFileName().toString().replaceFirst("\\.lang$", "");
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    Matcher m = Pattern.compile("^\\s*([^#=\\s][^=]*?)\\s*=").matcher(line.replace("﻿", ""));
                    if (m.find()) keys.add(prefix + "." + m.group(1));
                }
            }
        }
        return keys;
    }
}
