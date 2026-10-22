package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.taming.TamingApi;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.RelativeIntPosition;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/**
 * {@code /ranch care status|animals|run|fill}: admin tools for troughs, habitat and happiness, also usable by
 * the headless test player (which cannot use blocks).
 */
public final class CareCommand extends AbstractCommandCollection {
    public CareCommand() {
        super("care", "cozyranch_care.commands.care.desc");
        this.requireNoPermission();
        this.addSubCommand(new Status());
        this.addSubCommand(new Animals());
        this.addSubCommand(new Run());
        this.addSubCommand(new Fill());
    }

    @Nonnull
    private static String pct(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Troughs and houses of the player's world. */
    static final class Status extends AbstractPlayerCommand {
        Status() {
            super("status", "cozyranch_care.commands.care.status.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            CareService care = CareService.get();
            if (care == null) return;
            List<CareStore.TroughView> troughs = care.store().troughsIn(world.getName());
            player.sendMessage(Message.translation("cozyranch_care.status.troughs").param("count", troughs.size()).param("world", world.getName()));
            for (CareStore.TroughView t : troughs) {
                int items = t.contents().slots.stream().mapToInt(s -> s.count).sum();
                player.sendMessage(Message.translation("cozyranch_care.status.trough")
                    .param("x", t.x()).param("y", t.y()).param("z", t.z())
                    .param("stacks", t.contents().slots.size()).param("items", items));
            }
            List<HouseRecord> houses = Ranch.houses().inWorld(world.getName());
            player.sendMessage(Message.translation("cozyranch_care.status.houses").param("count", houses.size()));
            long now = System.currentTimeMillis();
            for (HouseRecord h : houses) {
                long ago = h.habitatScannedAtMs <= 0 ? -1 : (now - h.habitatScannedAtMs) / 1000;
                player.sendMessage(Message.translation("cozyranch_care.status.house")
                    .param("species", h.speciesId)
                    .param("x", h.x).param("y", h.y).param("z", h.z)
                    .param("met", h.habitatRequirementsMet)
                    .param("score", pct(h.habitatScore))
                    .param("missing", h.habitatMissing.isEmpty() ? "-" : String.join(", ", h.habitatMissing))
                    .param("seconds", ago));
            }
        }
    }

    /** The player's animals in this world: happiness now, its target and the parts of the target. */
    static final class Animals extends AbstractPlayerCommand {
        Animals() {
            super("animals", "cozyranch_care.commands.care.animals.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            List<AnimalRecord> mine = Ranch.animals().ownedBy(player.getUuid()).stream().filter(r -> world.getName().equals(r.worldName)).toList();
            if (mine.isEmpty()) {
                player.sendMessage(Message.translation("cozyranch_care.animals.none"));
                return;
            }
            long now = System.currentTimeMillis();
            for (AnimalRecord r : mine) {
                CareRules.Breakdown b = CareApi.happinessBreakdown(r);
                long fedAgo = r.lastFedAtMs <= 0 ? -1 : (now - r.lastFedAtMs) / 1000;
                Message species = SpeciesRegistry.byId(r.speciesId).map(s -> Message.translation(s.NameKey)).orElse(Message.raw(r.speciesId));
                player.sendMessage(Message.translation("cozyranch_care.animals.line")
                    .param("animal", TamingApi.displayName(r))
                    .param("species", species)
                    .param("happiness", pct(r.happiness))
                    .param("target", pct(b.target()))
                    .param("hearts", r.hearts)
                    .param("fed", fedAgo)
                    .param("meal", CareApi.lastTroughMeal(r.animalId).map(Enum::name).orElse("-")));
                player.sendMessage(Message.translation("cozyranch_care.animals.parts")
                    .param("base", pct(b.base())).param("food", pct(b.fed())).param("preferred", pct(b.preferred()))
                    .param("house", pct(b.house())).param("met", pct(b.habitatMet())).param("score", pct(b.habitatScore()))
                    .param("petted", pct(b.petted())).param("treat", pct(b.treat())));
            }
        }
    }

    /** Feeds and updates every animal of the world, rescans every house and checks every trough now. */
    static final class Run extends AbstractPlayerCommand {
        Run() {
            super("run", "cozyranch_care.commands.care.run.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            CareService care = CareService.get();
            if (care == null) return;
            int animals = CareWorld.of(world).runNow(world, care, System.currentTimeMillis());
            player.sendMessage(Message.translation("cozyranch_care.run.done")
                .param("animals", animals).param("houses", Ranch.houses().inWorld(world.getName()).size()));
        }
    }

    /** Puts food into the trough at a position, as Use with food would. */
    static final class Fill extends AbstractPlayerCommand {
        private final RequiredArg<RelativeIntPosition> coords;
        private final RequiredArg<String> item;
        private final RequiredArg<Integer> count;

        Fill() {
            super("fill", "cozyranch_care.commands.care.fill.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.coords = this.withRequiredArg("x y z", "cozyranch_care.commands.care.fill.arg.coords", ArgTypes.RELATIVE_BLOCK_POSITION);
            this.item = this.withRequiredArg("item", "cozyranch_care.commands.care.fill.arg.item", ArgTypes.STRING);
            this.count = this.withRequiredArg("count", "cozyranch_care.commands.care.fill.arg.count", ArgTypes.INTEGER);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            CareService care = CareService.get();
            if (care == null) return;
            String itemId = context.get(item);
            if (!CareRules.isAnyFood(SpeciesRegistry.all(), itemId)) {
                player.sendMessage(Message.translation("cozyranch_care.trough.not_food").param("item", TroughInteraction.itemName(itemId)));
                return;
            }
            Vector3i pos = TroughBlocks.base(world, new Vector3i(context.get(coords).getBlockPosition(context, store)));
            if (TroughBlocks.presence(world, pos.x, pos.y, pos.z) != TroughBlocks.Presence.TROUGH) {
                player.sendMessage(Message.translation("cozyranch_care.fill.no_trough").param("x", pos.x).param("y", pos.y).param("z", pos.z));
                return;
            }
            int put = care.store().add(world.getName(), pos.x, pos.y, pos.z, itemId, Math.max(0, context.get(count)), TroughBlocks::maxStack);
            CareWorld.syncTrough(world, care, pos.x, pos.y, pos.z);
            care.scheduleSave();
            player.sendMessage(Message.translation("cozyranch_care.fill.done").param("count", put).param("item", TroughInteraction.itemName(itemId))
                .param("x", pos.x).param("y", pos.y).param("z", pos.z));
        }
    }
}
