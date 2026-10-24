package com.hexvane.cozytalesranching.journal;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.breeding.TraitRegistry;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@code /ranch journal [species]}: opens the player's journal at the contents or at one animal's page.
 * Admin tools beside it: {@code demo <state>} (a made-up journal, nothing saved), {@code discover <species> <what>}
 * (writes into your own journal through the real rules) and {@code reset}.
 */
public final class JournalCommand extends AbstractPlayerCommand {
    public JournalCommand() {
        super("journal", "cozyranch_journal.commands.journal.desc");
        this.requirePermission(RanchPermissions.JOURNAL);
        this.addUsageVariant(new AtSpecies());
        this.addSubCommand(new Demo());
        this.addSubCommand(new Discover());
        this.addSubCommand(new Reset());
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!JournalPage.open(ref, store, null)) context.sendMessage(ServerUiOut.message(Txt.j("commands.journal.failed")));
    }

    @Nullable
    static SpeciesDefinition species(@Nonnull CommandContext context, @Nullable String id) {
        SpeciesDefinition s = id == null ? null : SpeciesRegistry.byId(id.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (s == null) {
            String known = JournalBook.get().ordered().stream().map(d -> d.Id).collect(Collectors.joining(", "));
            context.sendMessage(ServerUiOut.message(Txt.j("commands.journal.unknown").with("species", id == null ? "" : id).with("known", known)));
        }
        return s;
    }

    /** {@code /ranch journal <species>} */
    private static final class AtSpecies extends AbstractPlayerCommand {
        private final RequiredArg<String> speciesArg;

        AtSpecies() {
            super("cozyranch_journal.commands.journal.species.desc");
            this.requirePermission(RanchPermissions.JOURNAL);
            this.speciesArg = withRequiredArg("species", "cozyranch_journal.commands.journal.arg.species", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            SpeciesDefinition s = species(context, speciesArg.get(context));
            if (s != null && !JournalPage.open(ref, store, s.Id)) context.sendMessage(ServerUiOut.message(Txt.j("commands.journal.failed")));
        }
    }

    /** {@code /ranch journal demo <empty|partial|full>}: opens a made-up journal. Touches no saved data. */
    private static final class Demo extends AbstractPlayerCommand {
        private final RequiredArg<String> scenarioArg;

        Demo() {
            super("demo", "cozyranch_journal.commands.journal.demo.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.scenarioArg = withRequiredArg("state", "cozyranch_journal.commands.journal.demo.arg", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            JournalDemo.Scenario scenario = JournalDemo.scenario(scenarioArg.get(context));
            if (scenario == null) {
                context.sendMessage(ServerUiOut.message(Txt.j("commands.journal.demo.unknown")));
                return;
            }
            JournalBook book = JournalBook.get();
            PlayerJournal fixed = JournalDemo.create(scenario, playerRef.getUuid(), book, book.ordered(), TraitRegistry.get().ids());
            if (!JournalPage.openFixed(ref, store, fixed, null)) context.sendMessage(ServerUiOut.message(Txt.j("commands.journal.failed")));
        }
    }

    /** {@code /ranch journal discover <species> <what>}: one discovery in your own journal, as the event would make it. */
    private static final class Discover extends AbstractPlayerCommand {
        private static final List<String> WHAT = List.of("seen", "diet", "care", "house", "behaviour", "breeding", "trait", "product", "hearts", "visitor", "rare", "all");
        private final RequiredArg<String> speciesArg;
        private final RequiredArg<String> whatArg;

        Discover() {
            super("discover", "cozyranch_journal.commands.journal.discover.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.speciesArg = withRequiredArg("species", "cozyranch_journal.commands.journal.discover.arg.species", ArgTypes.STRING);
            this.whatArg = withRequiredArg("what", "cozyranch_journal.commands.journal.discover.arg.what", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            SpeciesDefinition s = species(context, speciesArg.get(context));
            if (s == null) return;
            String what = whatArg.get(context).trim().toLowerCase(Locale.ROOT);
            if (!WHAT.contains(what)) {
                context.sendMessage(ServerUiOut.message(Txt.j("commands.journal.discover.unknown")));
                return;
            }
            boolean any = false;
            for (String w : what.equals("all") ? WHAT.subList(0, WHAT.size() - 1) : List.of(what)) {
                Function<PlayerJournal, JournalRules.Change> edit = edit(s, w);
                JournalRules.Change[] out = new JournalRules.Change[1];
                JournalListeners.observe(playerRef.getUuid(), s.Id, j -> out[0] = edit.apply(j));
                any |= out[0] != null && out[0].any();
            }
            context.sendMessage(ServerUiOut.message(Txt.j(any ? "commands.journal.discover.done" : "commands.journal.discover.nothing")));
        }

        @Nonnull
        private static Function<PlayerJournal, JournalRules.Change> edit(@Nonnull SpeciesDefinition s, @Nonnull String what) {
            long now = System.currentTimeMillis();
            List<String> behaviours = JournalBook.get().page(s.Id).Behaviours;
            List<String> traits = TraitRegistry.get().ids();
            return switch (what) {
                case "seen", "diet" -> j -> JournalRules.tameAttempt(j, s.Id, now);
                case "care" -> j -> JournalRules.befriended(j, s.Id, null, List.of(), now);
                case "house" -> j -> JournalRules.housed(j, s.Id, true, now);
                case "behaviour" -> j -> {
                    String next = behaviours.stream().filter(b -> j.entry(s.Id) == null || !j.entry(s.Id).Behaviours.contains(b)).findFirst().orElse("");
                    return JournalRules.behaviour(j, s.Id, next, null, now);
                };
                case "breeding" -> j -> JournalRules.bred(j, s.Id, null, List.of(), now);
                case "trait" -> j -> {
                    String next = traits.stream().filter(t -> j.entry(s.Id) == null || !j.entry(s.Id).Traits.contains(t)).findFirst().orElse("");
                    return JournalRules.traits(j, s.Id, next.isEmpty() ? List.of() : List.of(next), now);
                };
                case "product" -> j -> {
                    String item = s.allProducts().isEmpty() ? "" : s.allProducts().getFirst().Item;
                    return JournalRules.produced(j, s.Id, item, now);
                };
                case "hearts" -> j -> JournalRules.hearts(j, s.Id, null, 4, 5, now);
                case "visitor" -> j -> JournalRules.visitor(j, s.Id, false, now);
                default -> j -> JournalRules.visitor(j, s.Id, true, now);
            };
        }
    }

    /** {@code /ranch journal reset}: forgets everything in your own journal. */
    private static final class Reset extends AbstractPlayerCommand {
        Reset() {
            super("reset", "cozyranch_journal.commands.journal.reset.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            JournalService service = JournalService.get();
            if (service == null) {
                context.sendMessage(ServerUiOut.message(Txt.j("commands.journal.failed")));
                return;
            }
            service.reset(playerRef.getUuid());
            context.sendMessage(ServerUiOut.message(Txt.j("commands.journal.reset.done")));
        }
    }
}
