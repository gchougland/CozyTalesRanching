package com.hexvane.cozytalesranching.journal;

import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Made-up journals for {@code /ranch journal demo} and the render tests, written through {@link JournalRules}. */
public final class JournalDemo {
    /** 22 October 2026, mid-morning UTC. */
    static final long START_MS = 1_792_663_200_000L;
    private static final long DAY_MS = 86_400_000L;
    private static final String[] NAMES = {"Clover", "Buttercup Featherbottom", "Truffles", "Woolsworth", "Lily"};

    public enum Scenario {
        /** Nothing found yet. */
        EMPTY,
        /** Two species met: the cow well known, the chicken only fed once. Three memories. */
        PARTIAL,
        /** Every species fully known, every trait (all on the first species), every memory (more than one spread). */
        FULL
    }

    private JournalDemo() {}

    @Nullable
    public static Scenario scenario(@Nullable String name) {
        if (name == null || name.isBlank()) return Scenario.PARTIAL;
        try {
            return Scenario.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nonnull
    public static PlayerJournal create(@Nonnull Scenario scenario, @Nonnull UUID player, @Nonnull JournalBook book, @Nonnull List<SpeciesDefinition> species,
                                       @Nonnull List<String> traitIds) {
        PlayerJournal j = new PlayerJournal();
        j.Player = player.toString();
        switch (scenario) {
            case EMPTY -> {}
            case PARTIAL -> {
                SpeciesDefinition cow = find(species, "cow");
                SpeciesDefinition chicken = find(species, "chicken");
                long t = START_MS;
                if (cow != null) {
                    JournalRules.tameAttempt(j, cow.Id, t);
                    JournalRules.befriended(j, cow.Id, "Clover", first(traitIds, 1), t + 600_000);
                    JournalRules.housed(j, cow.Id, true, t + DAY_MS);
                    List<String> b = book.page(cow.Id).Behaviours;
                    if (b.size() > 1) {
                        JournalRules.behaviour(j, cow.Id, b.get(0), "Clover", t + DAY_MS + 3_600_000);
                        JournalRules.behaviour(j, cow.Id, b.get(1), "Clover", t + 2 * DAY_MS);
                    }
                }
                if (chicken != null) JournalRules.tameAttempt(j, chicken.Id, t + 2 * DAY_MS);
            }
            case FULL -> {
                long t = START_MS;
                for (int i = 0; i < species.size(); i++) {
                    SpeciesDefinition s = species.get(i);
                    String name = NAMES[i % NAMES.length];
                    t += DAY_MS;
                    JournalRules.tameAttempt(j, s.Id, t);
                    JournalRules.befriended(j, s.Id, name, rotate(traitIds, i, 2), t + 600_000);
                    JournalRules.housed(j, s.Id, true, t + 3_600_000);
                    for (String b : book.page(s.Id).Behaviours) JournalRules.behaviour(j, s.Id, b, name, t + 7_200_000);
                    JournalRules.bred(j, s.Id, name, rotate(traitIds, i + 2, 2), t + 10_800_000);
                    for (SpeciesDefinition.Product p : s.Products) JournalRules.produced(j, s.Id, p.Item, t + 14_400_000);
                    JournalRules.hearts(j, s.Id, name, 4, 5, t + 18_000_000);
                    JournalRules.visitor(j, s.Id, true, t + 21_600_000);
                    // The first species has raised every trait there is: its page shows the longest traits list.
                    if (i == 0) JournalRules.traits(j, s.Id, traitIds, t + 25_200_000);
                }
            }
        }
        return j;
    }

    @Nullable
    private static SpeciesDefinition find(@Nonnull List<SpeciesDefinition> species, @Nonnull String id) {
        return species.stream().filter(s -> s.Id.equals(id)).findFirst().orElse(null);
    }

    @Nonnull
    private static List<String> first(@Nonnull List<String> ids, int n) {
        return ids.subList(0, Math.min(n, ids.size()));
    }

    @Nonnull
    private static List<String> rotate(@Nonnull List<String> ids, int from, int n) {
        if (ids.isEmpty()) return List.of();
        return java.util.stream.IntStream.range(0, Math.min(n, ids.size())).mapToObj(k -> ids.get((from * 2 + k) % ids.size())).toList();
    }
}
