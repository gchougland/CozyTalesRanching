package com.hexvane.cozytalesranching.journal;

import com.hexvane.cozytalesranching.breeding.TraitBook;
import com.hexvane.cozytalesranching.journal.PlayerJournal.Entry;
import com.hexvane.cozytalesranching.journal.PlayerJournal.Fact;
import com.hexvane.cozytalesranching.journal.PlayerJournal.Memory;
import com.hexvane.cozytalesranching.journal.PlayerJournal.MemoryKind;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** What each journal page shows, worked out from one player's journal. Pure: no engine types, testable. */
public final class JournalView {
    /** Memories per spread: three on each page. */
    public static final int MEMORIES_PER_PAGE = 3;
    public static final int MEMORIES_PER_SPREAD = MEMORIES_PER_PAGE * 2;
    /** Traits per spread: four under the left page's heading, five on the right. */
    public static final int TRAITS_LEFT = 4;
    public static final int TRAITS_RIGHT = 5;
    public static final int TRAITS_PER_SPREAD = TRAITS_LEFT + TRAITS_RIGHT;
    static final String COVER_ART = JournalBook.ART_DIR + "Cover.png";

    /** What the view needs from the server: item names, whether another role's lang key exists, the rules' numbers. */
    public interface Lookup {
        /** Translation key of an item's name, or null if there is no such item. */
        @Nullable
        String itemNameKey(@Nonnull String itemId);

        /** Whether an en-US string exists for the full key. */
        boolean hasKey(@Nonnull String key);

        int breedingMinHearts();

        @Nonnull
        ZoneId zone();
    }

    public record Context(@Nonnull JournalBook book, @Nonnull List<SpeciesDefinition> species, @Nonnull TraitBook traits, @Nonnull Lookup lookup) {}

    public record Card(@Nonnull String speciesId, @Nonnull Txt name, @Nonnull String art, boolean known) {}

    public record Contents(@Nonnull Txt keeper, @Nonnull Txt progress, @Nonnull Txt memories, @Nonnull List<Card> cards) {}

    public enum Style { BODY, STRONG, MUTED, RARE }

    public record Line(@Nonnull Txt text, @Nonnull Style style) {}

    public record Section(@Nonnull Txt title, @Nonnull List<Line> lines) {}

    public record SpeciesPage(@Nonnull String speciesId, @Nonnull Txt name, @Nonnull Txt tagline, @Nonnull String art, boolean known,
                              @Nonnull Txt description, @Nonnull List<Section> left, @Nonnull List<Section> right,
                              @Nonnull String previous, @Nonnull String next, @Nonnull Txt number) {}

    public record MemoryRow(@Nonnull Txt date, @Nonnull Txt title, @Nonnull Txt text, @Nonnull String art) {}

    public record Memories(int spread, int spreads, @Nonnull List<MemoryRow> left, @Nonnull List<MemoryRow> right, @Nonnull Txt pageLabel,
                           boolean hasPrevious, boolean hasNext) {}

    /** One trait on the traits pages: its name, what it does and which species it was seen on; or ??? and a hint. */
    public record TraitRow(boolean known, @Nonnull Txt name, @Nonnull Txt text, @Nullable Txt seen) {}

    public record Traits(int spread, int spreads, @Nonnull Txt progress, @Nonnull Txt intro, @Nonnull List<TraitRow> left, @Nonnull List<TraitRow> right,
                         @Nonnull Txt pageLabel, boolean hasPrevious, boolean hasNext) {}

    /** The How to pages: short sections of plain lines, the same rows as a species page. */
    public record Guide(@Nonnull List<Section> left, @Nonnull List<Section> right) {}

    /** One toggle on the settings page. */
    public record SettingRow(@Nonnull String id, @Nonnull Txt name, @Nonnull Txt description, @Nonnull Txt state, boolean on) {}

    /** The How to sections, in reading order, with how many lines each has ({@code guide.<id>.title}, {@code guide.<id>.<n>}). */
    static final String[][] GUIDE_LEFT = {{"friend", "4"}, {"home", "3"}, {"visitors", "1"}, {"happy", "3"}};
    static final String[][] GUIDE_RIGHT = {{"babies", "2"}, {"produce", "2"}, {"frogs", "2"}, {"wild", "3"}};

    private JournalView() {}

    // ---- contents ----

    @Nonnull
    public static Contents contents(@Nonnull PlayerJournal j, @Nonnull Context c, @Nonnull String playerName) {
        List<Card> cards = new ArrayList<>();
        int found = 0;
        for (SpeciesDefinition s : c.species()) {
            boolean known = j.knows(s.Id, Fact.SEEN);
            if (known) found++;
            cards.add(new Card(s.Id, known ? speciesName(s) : Txt.j("unknown"), known ? c.book().art(s.Id) : c.book().silhouette(s.Id), known));
        }
        Txt progress = Txt.j("contents.progress").with("found", found).with("total", c.species().size());
        int count = j.Memories.size();
        Txt memories = count == 0 ? Txt.j("contents.memories.none") : Txt.j(count == 1 ? "contents.memories.one" : "contents.memories.many").with("count", count);
        return new Contents(Txt.j("contents.keeper").with("name", Txt.raw(playerName)), progress, memories, cards);
    }

    // ---- species page ----

    @Nonnull
    public static SpeciesPage species(@Nonnull PlayerJournal j, @Nonnull Context c, @Nonnull SpeciesDefinition s) {
        Entry e = j.entry(s.Id);
        if (e == null) e = new Entry();
        boolean seen = e.knows(Fact.SEEN);
        JournalBook.SpeciesPage page = c.book().page(s.Id);

        Txt name = seen ? speciesName(s) : Txt.j("unknown");
        Txt tagline = seen && e.FirstSeenAtMs > 0 ? Txt.j("species.first_met").with("date", date(e.FirstSeenAtMs, c.lookup().zone())) : Txt.j("species.not_met");
        Txt description = seen
            ? (page.DescriptionKey == null || page.DescriptionKey.isBlank() ? Txt.j("species.no_description") : Txt.key(page.DescriptionKey))
            : Txt.j("species.unmet_description");

        List<Section> left = new ArrayList<>();
        left.add(diet(e, s, c));
        left.add(treats(e, s, c));

        List<Section> right = new ArrayList<>();
        right.add(care(e, s));
        right.add(home(e, s, c));
        right.add(wildlife(e, s, c));
        right.add(breeding(e, s, c));
        right.add(products(e, s, c));
        Section looks = looks(e, page);
        if (looks != null) right.add(looks);
        right.add(traits(e, c));
        right.add(behaviours(e, page));
        Section rare = rare(e, s, page, c);
        if (rare != null) right.add(rare);

        List<SpeciesDefinition> all = c.species();
        int i = Math.max(0, indexOf(all, s.Id));
        String previous = all.isEmpty() ? s.Id : all.get((i - 1 + all.size()) % all.size()).Id;
        String next = all.isEmpty() ? s.Id : all.get((i + 1) % all.size()).Id;
        Txt number = Txt.j("species.number").with("number", i + 1).with("total", Math.max(1, all.size()));
        return new SpeciesPage(s.Id, name, tagline, seen ? c.book().art(s.Id) : c.book().silhouette(s.Id), seen, description, left, right,
            previous, next, number);
    }

    @Nonnull
    private static Section diet(@Nonnull Entry e, @Nonnull SpeciesDefinition s, @Nonnull Context c) {
        if (!e.knows(Fact.DIET)) return unknown("section.diet", "hint.diet");
        List<Line> lines = new ArrayList<>();
        if (!s.BasicFoods.isEmpty()) lines.add(body(Txt.j("diet.eats").with("foods", list(items(s.BasicFoods, c)))));
        if (!s.PreferredFoods.isEmpty()) lines.add(body(Txt.j("diet.prefers").with("foods", list(items(s.PreferredFoods, c)))));
        if (lines.isEmpty()) lines.add(muted(Txt.j("diet.none")));
        return new Section(Txt.j("section.diet"), lines);
    }

    @Nonnull
    private static Section treats(@Nonnull Entry e, @Nonnull SpeciesDefinition s, @Nonnull Context c) {
        if (!e.knows(Fact.CARE)) return unknown("section.treats", "hint.treats");
        if (s.FavoriteTreats.isEmpty()) return new Section(Txt.j("section.treats"), List.of(muted(Txt.j("treats.none"))));
        return new Section(Txt.j("section.treats"), List.of(body(Txt.j("treats.line").with("foods", list(items(s.FavoriteTreats, c))))));
    }

    @Nonnull
    private static Section care(@Nonnull Entry e, @Nonnull SpeciesDefinition s) {
        if (!e.knows(Fact.CARE)) return unknown("section.care", "hint.care");
        return new Section(Txt.j("section.care"), List.of(
            body(Txt.j(s.FeedingsToTame == 1 ? "care.tame.one" : "care.tame.many").with("count", Math.max(1, s.FeedingsToTame))),
            body(Txt.j("care.daily"))));
    }

    @Nonnull
    private static Section home(@Nonnull Entry e, @Nonnull SpeciesDefinition s, @Nonnull Context c) {
        if (!e.knows(Fact.HABITAT)) return unknown("section.home", "hint.home");
        List<Line> lines = new ArrayList<>();
        String houseKey = s.HouseBlock.isBlank() ? null : c.lookup().itemNameKey(s.HouseBlock);
        Txt house = houseKey != null ? Txt.key(houseKey) : Txt.j("home.house_fallback");
        lines.add(body(Txt.j("home.house").with("house", house).with("count", Math.max(1, s.HouseCapacity))));
        if (!s.HabitatRequires.isEmpty()) lines.add(body(Txt.j("home.needs").with("things", list(habitats(s.HabitatRequires)))));
        if (!s.HabitatPrefers.isEmpty()) lines.add(body(Txt.j("home.likes").with("things", list(habitats(s.HabitatPrefers)))));
        return new Section(Txt.j("section.home"), lines);
    }

    @Nonnull
    private static Section wildlife(@Nonnull Entry e, @Nonnull SpeciesDefinition s, @Nonnull Context c) {
        if (!e.knows(Fact.WILDLIFE)) return unknown("section.wildlife", "hint.wildlife");
        String key = wildlifeKey(s.Id);
        Txt hint = c.lookup().hasKey(key) ? Txt.key(key) : Txt.j("wildlife.fallback");
        return new Section(Txt.j("section.wildlife"), List.of(body(hint)));
    }

    @Nonnull
    private static Section breeding(@Nonnull Entry e, @Nonnull SpeciesDefinition s, @Nonnull Context c) {
        if (!e.knows(Fact.BREEDING)) return unknown("section.breeding", "hint.breeding");
        int hearts = c.lookup().breedingMinHearts();
        Txt who = hearts <= 0 ? Txt.j("breeding.pair.any") : Txt.j(hearts == 1 ? "breeding.pair.one" : "breeding.pair.many").with("hearts", hearts);
        long minutes = Math.max(1, Math.round(s.GrowUpMinutes));
        return new Section(Txt.j("section.breeding"), List.of(
            body(who),
            body(Txt.j(minutes == 1 ? "breeding.grow.one" : "breeding.grow.many").with("minutes", minutes))));
    }

    @Nonnull
    private static Section products(@Nonnull Entry e, @Nonnull SpeciesDefinition s, @Nonnull Context c) {
        Set<String> all = new LinkedHashSet<>();
        for (SpeciesDefinition.Product p : s.allProducts()) if (p != null && p.Item != null && !p.Item.isBlank()) all.add(p.Item);
        List<String> known = all.stream().filter(e.Products::contains).toList();
        if (all.isEmpty()) {
            return e.knows(Fact.CARE) ? new Section(Txt.j("section.products"), List.of(muted(Txt.j("products.none"))))
                : unknown("section.products", "hint.products");
        }
        if (known.isEmpty()) return unknown("section.products", "hint.products");
        List<Line> lines = new ArrayList<>();
        lines.add(body(Txt.j("products.line").with("items", list(items(known, c)))));
        int more = all.size() - known.size();
        if (more > 0) lines.add(muted(more(more)));
        return new Section(Txt.j("section.products"), lines);
    }

    /** The species' looks and how to get each one, once the species has been seen; null when the page lists none. */
    @Nullable
    static Section looks(@Nonnull Entry e, @Nonnull JournalBook.SpeciesPage page) {
        if (page.Looks == null || page.Looks.isEmpty()) return null;
        Txt title = page.LooksTitleKey != null && !page.LooksTitleKey.isBlank() ? Txt.key(page.LooksTitleKey) : Txt.j("section.looks");
        if (!e.knows(Fact.SEEN)) return new Section(title, List.of(muted(Txt.j("hint.looks"))));
        List<Line> lines = new ArrayList<>();
        for (JournalBook.Look l : page.Looks) {
            if (l == null || l.NameKey == null || l.NameKey.isBlank()) continue;
            lines.add(new Line(Txt.key(l.NameKey), Style.STRONG));
            if (l.HowKey != null && !l.HowKey.isBlank()) lines.add(body(Txt.key(l.HowKey)));
        }
        return lines.isEmpty() ? null : new Section(title, lines);
    }

    /** Each trait seen on this species: its name, then what it does. */
    @Nonnull
    private static Section traits(@Nonnull Entry e, @Nonnull Context c) {
        List<TraitBook.Trait> all = c.traits().Traits;
        List<Line> lines = new ArrayList<>();
        int known = 0;
        for (TraitBook.Trait t : all) {
            if (!e.Traits.contains(t.Id)) continue;
            known++;
            lines.add(new Line(traitName(t), Style.STRONG));
            lines.add(body(traitText(t)));
        }
        if (known == 0) return unknown("section.traits", "hint.traits");
        int more = all.size() - known;
        if (more > 0) lines.add(muted(more(more)));
        return new Section(Txt.j("section.traits"), lines);
    }

    @Nonnull
    private static Section behaviours(@Nonnull Entry e, @Nonnull JournalBook.SpeciesPage page) {
        List<Line> lines = new ArrayList<>();
        int unknown = 0;
        for (String id : page.Behaviours) {
            if (e.Behaviours.contains(id)) lines.add(body(behaviourNote(id)));
            else unknown++;
        }
        if (lines.isEmpty()) return unknown("section.behaviours", "hint.behaviours");
        if (unknown > 0) lines.add(muted(more(unknown)));
        return new Section(Txt.j("section.behaviours"), lines);
    }

    @Nullable
    private static Section rare(@Nonnull Entry e, @Nonnull SpeciesDefinition s, @Nonnull JournalBook.SpeciesPage page, @Nonnull Context c) {
        if (!e.knows(Fact.RARE)) return null;
        String wildKey = wildlifeKey(s.Id) + ".rare";
        Txt line;
        if (c.lookup().hasKey(wildKey)) line = Txt.key(wildKey);
        else if (page.RareKey != null && !page.RareKey.isBlank()) line = Txt.key(page.RareKey);
        else line = Txt.j("rare.fallback");
        return new Section(Txt.j("section.rare"), List.of(new Line(line, Style.RARE)));
    }

    // ---- memories ----

    @Nonnull
    public static Memories memories(@Nonnull PlayerJournal j, @Nonnull Context c, int spread) {
        List<Memory> all = j.Memories;
        int spreads = Math.max(1, (all.size() + MEMORIES_PER_SPREAD - 1) / MEMORIES_PER_SPREAD);
        int s = Math.clamp(spread, 0, spreads - 1);
        List<MemoryRow> left = new ArrayList<>(), right = new ArrayList<>();
        for (int i = s * MEMORIES_PER_SPREAD; i < Math.min(all.size(), (s + 1) * MEMORIES_PER_SPREAD); i++) {
            MemoryRow row = memoryRow(all.get(i), j, c);
            (i - s * MEMORIES_PER_SPREAD < MEMORIES_PER_PAGE ? left : right).add(row);
        }
        Txt label = Txt.j("memories.page").with("page", s + 1).with("pages", spreads);
        return new Memories(s, spreads, left, right, label, s > 0, s + 1 < spreads);
    }

    @Nonnull
    static MemoryRow memoryRow(@Nonnull Memory m, @Nonnull PlayerJournal j, @Nonnull Context c) {
        MemoryKind kind = MemoryKind.byId(m.Kind);
        SpeciesDefinition s = c.species().stream().filter(d -> d.Id.equals(m.Species)).findFirst().orElse(null);
        Txt species = s != null ? speciesName(s) : Txt.raw(m.Species);
        String base = "memory." + kind.id;
        Txt text = Txt.j(base + (m.Name != null ? ".named" : ".text")).with("species", species);
        if (m.Name != null) text.with("name", Txt.raw(m.Name));
        if (kind == MemoryKind.FIRST_BEHAVIOUR) text.with("behaviour", m.Detail == null ? Txt.j("unknown") : behaviourName(m.Detail));
        String art = s != null && j.knows(s.Id, Fact.SEEN) ? c.book().art(s.Id) : COVER_ART;
        return new MemoryRow(date(m.AtMs, c.lookup().zone()), Txt.j(base + ".title"), text, art);
    }

    // ---- traits ----

    /** Every trait in the trait book, in its order: name, effect and where seen once any animal of the player had it. */
    @Nonnull
    public static Traits traits(@Nonnull PlayerJournal j, @Nonnull Context c, int spread) {
        List<TraitBook.Trait> all = c.traits().Traits;
        int spreads = Math.max(1, (all.size() + TRAITS_PER_SPREAD - 1) / TRAITS_PER_SPREAD);
        int s = Math.clamp(spread, 0, spreads - 1);
        int found = 0;
        for (TraitBook.Trait t : all) if (!seenOn(j, c, t.Id).isEmpty()) found++;
        List<TraitRow> left = new ArrayList<>(), right = new ArrayList<>();
        for (int i = s * TRAITS_PER_SPREAD; i < Math.min(all.size(), (s + 1) * TRAITS_PER_SPREAD); i++) {
            TraitBook.Trait t = all.get(i);
            List<Txt> seen = seenOn(j, c, t.Id);
            TraitRow row = seen.isEmpty()
                ? new TraitRow(false, Txt.j("unknown"), Txt.j("traits.unknown"), null)
                : new TraitRow(true, traitName(t), traitText(t), Txt.j("traits.seen").with("species", list(seen)));
            (i - s * TRAITS_PER_SPREAD < TRAITS_LEFT ? left : right).add(row);
        }
        Txt progress = Txt.j("traits.progress").with("found", found).with("total", all.size());
        Txt label = Txt.j("traits.page").with("page", s + 1).with("pages", spreads);
        return new Traits(s, spreads, progress, Txt.j("traits.intro").with("max", c.traits().MaxTraits), left, right, label, s > 0, s + 1 < spreads);
    }

    /** Names of the species (book order) whose journal entry has the trait. */
    @Nonnull
    private static List<Txt> seenOn(@Nonnull PlayerJournal j, @Nonnull Context c, @Nonnull String traitId) {
        List<Txt> out = new ArrayList<>();
        for (SpeciesDefinition s : c.species()) {
            Entry e = j.entry(s.Id);
            if (e != null && e.Traits.contains(traitId)) out.add(speciesName(s));
        }
        return out;
    }

    @Nonnull
    static Txt traitName(@Nonnull TraitBook.Trait t) {
        return t.NameKey == null || t.NameKey.isBlank() ? Txt.raw(prettify(t.Id)) : Txt.key(t.NameKey);
    }

    @Nonnull
    static Txt traitText(@Nonnull TraitBook.Trait t) {
        return t.DescKey == null || t.DescKey.isBlank() ? Txt.j("traits.no_description") : Txt.key(t.DescKey);
    }

    // ---- pieces ----

    @Nonnull
    static Txt speciesName(@Nonnull SpeciesDefinition s) {
        return s.NameKey == null || s.NameKey.isBlank() ? Txt.raw(s.Id) : Txt.key(s.NameKey);
    }

    @Nonnull
    static Txt date(long atMs, @Nonnull ZoneId zone) {
        ZonedDateTime t = Instant.ofEpochMilli(atMs).atZone(zone);
        return Txt.j("date").with("month", Txt.j("month." + t.getMonthValue())).with("day", t.getDayOfMonth()).with("year", t.getYear());
    }

    /** {@code a}, {@code a and b}, {@code a, b and c}, ... up to four, then the first three and how many more. */
    @Nonnull
    static Txt list(@Nonnull List<Txt> parts) {
        String[] names = {"a", "b", "c", "d"};
        if (parts.isEmpty()) return Txt.j("unknown");
        if (parts.size() > 4) {
            Txt t = Txt.j("list.many").with("count", parts.size() - 3);
            for (int i = 0; i < 3; i++) t.with(names[i], parts.get(i));
            return t;
        }
        Txt t = Txt.j("list." + parts.size());
        for (int i = 0; i < parts.size(); i++) t.with(names[i], parts.get(i));
        return t;
    }

    @Nonnull
    private static List<Txt> items(@Nonnull List<String> itemIds, @Nonnull Context c) {
        List<Txt> out = new ArrayList<>();
        for (String id : itemIds) {
            String key = c.lookup().itemNameKey(id);
            out.add(key != null ? Txt.key(key) : Txt.raw(prettify(id)));
        }
        return out;
    }

    @Nonnull
    private static List<Txt> habitats(@Nonnull List<String> ids) {
        List<Txt> out = new ArrayList<>();
        for (String id : ids) out.add(Txt.key(habitatKey(id)));
        return out;
    }

    /** The house page's names for habitat features. */
    @Nonnull
    static String habitatKey(@Nonnull String id) {
        return "cozyranch_houseui" + ".habitat." + id;
    }

    /** The wildlife role's hint for what a happy home of this species attracts. */
    @Nonnull
    static String wildlifeKey(@Nonnull String speciesId) {
        return "cozyranch_wildlife" + ".hint." + speciesId;
    }

    @Nonnull
    static Txt behaviourNote(@Nonnull String id) {
        return Txt.j("behaviour." + id + ".note");
    }

    @Nonnull
    static Txt behaviourName(@Nonnull String id) {
        return Txt.j("behaviour." + id + ".name");
    }

    @Nonnull
    private static Txt more(int count) {
        return Txt.j(count == 1 ? "more.one" : "more.many").with("count", count);
    }

    @Nonnull
    private static Section unknown(@Nonnull String title, @Nonnull String hint) {
        return new Section(Txt.j(title), List.of(muted(Txt.j(hint))));
    }

    @Nonnull
    private static Line body(@Nonnull Txt t) {
        return new Line(t, Style.BODY);
    }

    @Nonnull
    private static Line muted(@Nonnull Txt t) {
        return new Line(t, Style.MUTED);
    }

    private static int indexOf(@Nonnull List<SpeciesDefinition> all, @Nonnull String id) {
        for (int i = 0; i < all.size(); i++) if (all.get(i).Id.equals(id)) return i;
        return -1;
    }

    /** {@code Plant_Crop_Wheat_Item} reads as {@code Wheat} when the item is unknown. */
    @Nonnull
    static String prettify(@Nonnull String itemId) {
        String s = itemId.replaceFirst("^(Plant_Crop_|Food_|Ingredient_|CozyRanch_Food_|CozyRanch_)", "").replaceFirst("_Item$", "");
        s = s.replace('_', ' ').trim();
        return s.isEmpty() ? itemId : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    // ---- how to and settings ----

    @Nonnull
    public static Guide guide() {
        return new Guide(guideSections(GUIDE_LEFT), guideSections(GUIDE_RIGHT));
    }

    @Nonnull
    private static List<Section> guideSections(@Nonnull String[][] spec) {
        List<Section> out = new ArrayList<>();
        for (String[] s : spec) {
            List<Line> lines = new ArrayList<>();
            for (int i = 1; i <= Integer.parseInt(s[1]); i++) lines.add(new Line(Txt.j("guide." + s[0] + "." + i), Style.BODY));
            out.add(new Section(Txt.j("guide." + s[0] + ".title"), lines));
        }
        return out;
    }

    /** The settings page's rows for one player's current choices. */
    @Nonnull
    public static List<SettingRow> settings(boolean chat, boolean sounds) {
        return List.of(setting("chat", chat), setting("sounds", sounds));
    }

    @Nonnull
    private static SettingRow setting(@Nonnull String id, boolean on) {
        return new SettingRow(id, Txt.j("settings." + id + ".name"), Txt.j("settings." + id + ".desc"), Txt.j(on ? "settings.on" : "settings.off"), on);
    }
}
