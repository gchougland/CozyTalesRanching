package com.hexvane.cozytalesranching.journal;

import java.util.List;
import javax.annotation.Nonnull;

/** Writes a journal view as UI commands. Every page is rebuilt whole. */
public final class JournalLayout {
    public static final String CONTENTS = "CozyRanch/JournalContents.ui";
    public static final String SPECIES = "CozyRanch/JournalSpecies.ui";
    public static final String MEMORIES = "CozyRanch/JournalMemories.ui";
    static final String CARD = "CozyRanch/JournalCard.ui";
    static final String HEADING = "CozyRanch/JournalHeading.ui";
    static final String LINE = "CozyRanch/JournalLine.ui";
    static final String LINE_MUTED = "CozyRanch/JournalLineMuted.ui";
    static final String LINE_RARE = "CozyRanch/JournalLineRare.ui";
    static final String MEMORY = "CozyRanch/JournalMemory.ui";

    public static final String ACTION_CONTENTS = "contents";
    public static final String ACTION_SPECIES = "species";
    public static final String ACTION_MEMORIES = "memories";

    private JournalLayout() {}

    public static void contents(@Nonnull JournalView.Contents v, @Nonnull UiOut ui) {
        ui.append(CONTENTS);
        ui.text("#JournalTitle", Txt.j("contents.title"));
        ui.text("#Intro", Txt.j("contents.intro"));
        ui.text("#MemoriesButton", Txt.j("nav.memories"));
        ui.text("#AnimalsTitle", Txt.j("contents.animals"));
        ui.text("#Keeper", v.keeper());
        ui.text("#Progress", v.progress());
        ui.text("#MemoryCount", v.memories());
        ui.click("#MemoriesButton", ACTION_MEMORIES, "0");
        List<JournalView.Card> cards = v.cards();
        for (int i = 0; i < cards.size(); i++) {
            JournalView.Card card = cards.get(i);
            String row = "#Cards[" + i + "]";
            ui.append("#Cards", CARD);
            ui.set(row + " #CardArt.AssetPath", card.art());
            ui.text(row + " #CardName", card.name());
            ui.click(row, ACTION_SPECIES, card.speciesId());
        }
    }

    public static void species(@Nonnull JournalView.SpeciesPage v, @Nonnull UiOut ui) {
        ui.append(SPECIES);
        ui.text("#SpeciesName", v.name());
        ui.text("#SpeciesTagline", v.tagline());
        ui.set("#SpeciesArt.AssetPath", v.art());
        ui.text("#SpeciesDesc", v.description());
        ui.text("#SpeciesNumber", v.number());
        sections("#LeftNotes", v.left(), ui);
        sections("#RightNotes", v.right(), ui);
        nav("#NavContents", "#NavPrev", "#NavNext", ui);
        ui.click("#NavContents", ACTION_CONTENTS, "");
        ui.click("#NavPrev", ACTION_SPECIES, v.previous());
        ui.click("#NavNext", ACTION_SPECIES, v.next());
    }

    public static void memories(@Nonnull JournalView.Memories v, @Nonnull UiOut ui) {
        ui.append(MEMORIES);
        ui.text("#MemoriesTitle", Txt.j("memories.title"));
        ui.text("#MemoriesSubtitle", Txt.j("memories.subtitle"));
        nav("#MemNavContents", "#MemNavPrev", "#MemNavNext", ui);
        boolean empty = v.left().isEmpty();
        ui.set("#MemoriesEmpty.Visible", empty);
        if (empty) ui.text("#MemoriesEmpty", Txt.j("memories.empty"));
        rows("#MemoryLeft", v.left(), ui);
        rows("#MemoryRight", v.right(), ui);
        ui.text("#MemoryPageLabel", v.pageLabel());
        ui.set("#MemNavPrev.Disabled", !v.hasPrevious());
        ui.set("#MemNavNext.Disabled", !v.hasNext());
        ui.click("#MemNavContents", ACTION_CONTENTS, "");
        if (v.hasPrevious()) ui.click("#MemNavPrev", ACTION_MEMORIES, String.valueOf(v.spread() - 1));
        if (v.hasNext()) ui.click("#MemNavNext", ACTION_MEMORIES, String.valueOf(v.spread() + 1));
    }

    /** Static labels are set here: a .ui {@code %key} reference cannot contain the underscore in our lang file names. */
    private static void nav(@Nonnull String contents, @Nonnull String previous, @Nonnull String next, @Nonnull UiOut ui) {
        ui.text(contents, Txt.j("nav.contents"));
        ui.text(previous, Txt.j("nav.previous"));
        ui.text(next, Txt.j("nav.next"));
    }

    private static void sections(@Nonnull String container, @Nonnull List<JournalView.Section> sections, @Nonnull UiOut ui) {
        int i = 0;
        for (JournalView.Section s : sections) {
            ui.append(container, HEADING);
            ui.text(container + "[" + i++ + "]", s.title());
            for (JournalView.Line line : s.lines()) {
                ui.append(container, switch (line.style()) {
                    case BODY -> LINE;
                    case MUTED -> LINE_MUTED;
                    case RARE -> LINE_RARE;
                });
                ui.text(container + "[" + i++ + "]", line.text());
            }
        }
    }

    private static void rows(@Nonnull String container, @Nonnull List<JournalView.MemoryRow> rows, @Nonnull UiOut ui) {
        for (int i = 0; i < rows.size(); i++) {
            JournalView.MemoryRow r = rows.get(i);
            String row = container + "[" + i + "]";
            ui.append(container, MEMORY);
            ui.set(row + " #MemArt.AssetPath", r.art());
            ui.text(row + " #MemDate", r.date());
            ui.text(row + " #MemTitle", r.title());
            ui.text(row + " #MemText", r.text());
        }
    }
}
