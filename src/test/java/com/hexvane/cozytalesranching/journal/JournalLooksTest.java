package com.hexvane.cozytalesranching.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hexvane.cozytalesranching.breeding.TraitRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalLooksTest {
    private static JournalBook.Look look(String name, String how) {
        JournalBook.Look l = new JournalBook.Look();
        l.NameKey = name;
        l.HowKey = how;
        return l;
    }

    private static PlayerJournal.Entry seen() {
        PlayerJournal.Entry e = new PlayerJournal.Entry();
        e.Facts.add(PlayerJournal.Fact.SEEN.id);
        return e;
    }

    @Test
    void aPageWithoutLooksHasNoSection() {
        assertNull(JournalView.looks(seen(), new JournalBook.SpeciesPage()));
    }

    @Test
    void eachLookIsANameThenHowToGetIt() {
        JournalBook.SpeciesPage page = new JournalBook.SpeciesPage();
        page.Looks = List.of(look("a.frost", "a.frost.how"), look("a.void", "a.void.how"));
        page.LooksTitleKey = "a.title";
        JournalView.Section s = JournalView.looks(seen(), page);
        assertNotNull(s);
        assertEquals("a.title", s.title().key());
        assertEquals(List.of("a.frost", "a.frost.how", "a.void", "a.void.how"), s.lines().stream().map(l -> l.text().key()).toList());
        assertEquals(JournalView.Style.STRONG, s.lines().get(0).style());
        assertEquals(JournalView.Style.BODY, s.lines().get(1).style());
    }

    @Test
    void anUnmetSpeciesShowsOnlyAHint() {
        JournalBook.SpeciesPage page = new JournalBook.SpeciesPage();
        page.Looks = List.of(look("a.frost", "a.frost.how"));
        JournalView.Section s = JournalView.looks(new PlayerJournal.Entry(), page);
        assertNotNull(s);
        assertEquals("cozyranch_journal.section.looks", s.title().key());
        assertEquals(List.of("cozyranch_journal.hint.looks"), s.lines().stream().map(l -> l.text().key()).toList());
    }

    @Test
    void theSnailPageListsSixShells() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/Server/CozyTalesRanching/Species/Snail/Journal.json")) {
            assertNotNull(in);
            JournalBook b = JournalBook.read(new InputStreamReader(in, StandardCharsets.UTF_8));
            assertEquals(6, b.page("snail").Looks.size());
        }
    }

    /**
     * The snail page in the full demo has a Shells section after Products. With {@code SNAIL_WRITE_COMMANDS=true} the
     * page's commands are written to {@code build/snail-journal-commands.json} for {@code tests/hytale/species-snail.test.json}.
     */
    @Test
    void snailPageShowsTheShellsSection(@TempDir Path data) throws Exception {
        SpeciesRegistry.load(data);
        TraitRegistry.load(data);
        JournalBook book;
        try (var in = Files.newInputStream(JournalLayoutTest.RES.resolve("Server/CozyTalesRanching/Journal.json"))) {
            book = JournalBook.read(com.hexvane.cozytalesranching.species.SpeciesOverlay.merged(in, "Journal"));
        }
        JournalView.Context c = new JournalView.Context(book, book.ordered(), TraitRegistry.get(), new JournalLayoutTest.TestLookup());
        PlayerJournal j = JournalDemo.create(JournalDemo.Scenario.FULL, JournalLayoutTest.PLAYER, book, c.species(), TraitRegistry.get().ids());
        JournalView.SpeciesPage page = JournalView.species(j, c, SpeciesRegistry.byId("snail").orElseThrow());
        List<String> titles = page.right().stream().map(s -> s.title().key()).toList();
        int products = titles.indexOf("cozyranch_journal.section.products");
        assertEquals("cozyranch_journal.section.shells", titles.get(products + 1));
        assertEquals(12, page.right().get(products + 1).lines().size());
        JournalLayoutTest.Recorder ui = new JournalLayoutTest.Recorder();
        JournalLayout.species(page, ui);
        if ("true".equals(System.getenv("SNAIL_WRITE_COMMANDS"))) {
            Files.createDirectories(Path.of("build"));
            Files.writeString(Path.of("build/snail-journal-commands.json"), ui.json(), StandardCharsets.UTF_8);
        }
    }
}
