package com.hexvane.cozytalesranching.produce;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** {@code /ranch produce poop status|now|here|break|logon|logoff}: admin test tools for poop piles, usable by the headless player. */
final class PoopCommand extends AbstractCommandCollection {
    PoopCommand() {
        super("poop", "cozyranch_produce.commands.poop.desc");
        this.requireNoPermission();
        this.addSubCommand(new Status());
        this.addSubCommand(new Now());
        this.addSubCommand(new Here());
        this.addSubCommand(new Break());
        this.addSubCommand(new Log(true));
        this.addSubCommand(new Log(false));
    }

    /** Every known pile in this world with its count and size, and the last pass. */
    static final class Status extends AbstractPlayerCommand {
        Status() {
            super("status", "cozyranch_produce.commands.poop.status.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            PoopService s = ProduceFeature.poop();
            if (s == null) return;
            List<PoopRules.Pile> piles = s.known(world);
            PoopTuning t = PoopTuning.get();
            context.sendMessage(Message.translation("cozyranch_produce.poop.status.header")
                .param("count", piles.size()).param("on", t.Enabled ? "on" : "off").param("last", s.lastPass()));
            for (PoopRules.Pile p : piles) {
                context.sendMessage(Message.translation("cozyranch_produce.poop.status.pile")
                    .param("x", p.x()).param("y", p.y()).param("z", p.z()).param("count", p.count())
                    .param("size", PoopRules.sizeOf(p.count()).name().toLowerCase(java.util.Locale.ROOT)));
            }
        }
    }

    /** Every eligible animal in this world makes a dropping now (no chance, cooldown or time of day; all placement rules). */
    static final class Now extends AbstractPlayerCommand {
        Now() {
            super("now", "cozyranch_produce.commands.poop.now.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            PoopService s = ProduceFeature.poop();
            if (s == null) return;
            PoopService.PassResult r = s.pass(world, true);
            context.sendMessage(Message.translation("cozyranch_produce.poop.now.done")
                .param("eligible", r.eligible()).param("placed", r.placed()).param("merged", r.merged())
                .param("skipped", r.skipped().toString()));
        }
    }

    /** One dropping at the player's feet, with the same rules as an animal of the nearest house within its range. */
    static final class Here extends AbstractPlayerCommand {
        Here() {
            super("here", "cozyranch_produce.commands.poop.here.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            PoopService s = ProduceFeature.poop();
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (s == null || t == null) return;
            Vector3d p = t.getPosition();
            PoopService.Outcome o = s.drop(world, new PoopBlocks(world), nearestHouse(world, p),
                (int) Math.floor(p.x), (int) Math.floor(p.y + 0.05), (int) Math.floor(p.z));
            context.sendMessage(Message.translation("cozyranch_produce.poop.here.done")
                .param("what", o.what()).param("x", o.x()).param("y", o.y()).param("z", o.z()).param("count", o.count()));
        }

        @Nullable
        private static HouseRecord nearestHouse(@Nonnull World world, @Nonnull Vector3d p) {
            HouseRecord best = null;
            double bestD = 24 * 24;
            for (HouseRecord h : Ranch.houses().inWorld(world.getName())) {
                double dx = h.doorX - p.x, dz = h.doorZ - p.z, d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = h;
                }
            }
            return best;
        }
    }

    /**
     * Breaks the nearest known pile within 10 blocks as the player would by hand (the engine's own block damage path,
     * so the block's state, gathering and drop list decide the drops), then counts the dropped poop beside it.
     */
    static final class Break extends AbstractPlayerCommand {
        Break() {
            super("break", "cozyranch_produce.commands.poop.break.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            PoopService s = ProduceFeature.poop();
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (s == null || t == null) return;
            Vector3d p = t.getPosition();
            PoopRules.Pile best = null;
            double bestD = 10 * 10;
            for (PoopRules.Pile pile : s.known(world)) {
                double dx = pile.x() + 0.5 - p.x, dz = pile.z() + 0.5 - p.z, d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = pile;
                }
            }
            if (best == null) {
                context.sendMessage(Message.translation("cozyranch_produce.poop.break.none"));
                return;
            }
            PoopRules.Pile pile = best;
            Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(pile.x(), pile.y(), pile.z());
            if (section == null || !section.isValid()) return;
            int before = poopNear(store, pile);
            boolean broke = BlockHarvestUtils.performBlockDamage(ref, ref, new Vector3i(pile.x(), pile.y(), pile.z()), null, null, null, false, 1f,
                SetBlockSettings.NONE, false, section, store, world.getChunkStore().getStore());
            // the drops are added through a command buffer: count them a moment later
            world.execute(() -> world.execute(() -> {
                int dropped = poopNear(store, pile) - before;
                int after = new PoopBlocks(world).count(pile.x(), pile.y(), pile.z());
                context.sendMessage(Message.translation("cozyranch_produce.poop.break.done")
                    .param("x", pile.x()).param("y", pile.y()).param("z", pile.z()).param("count", pile.count())
                    .param("broke", String.valueOf(broke)).param("dropped", dropped).param("after", after));
            }));
        }
    }

    /** Poop lying as dropped items within 3 blocks of a pile cell. */
    static int poopNear(@Nonnull Store<EntityStore> store, @Nonnull PoopRules.Pile pile) {
        int[] n = {0};
        store.forEachChunk(ItemComponent.getComponentType(), (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                ItemComponent item = chunk.getComponent(i, ItemComponent.getComponentType());
                TransformComponent it = chunk.getComponent(i, TransformComponent.getComponentType());
                if (item == null || it == null || item.getItemStack() == null) continue;
                if (!PoopRules.ITEM_DROP.equals(item.getItemStack().getItemId())) continue;
                Vector3d q = it.getPosition();
                if (Math.abs(q.x - pile.x() - 0.5) < 3 && Math.abs(q.z - pile.z() - 0.5) < 3 && Math.abs(q.y - pile.y()) < 3) n[0] += item.getItemStack().getQuantity();
            }
        });
        return n[0];
    }

    static final class Log extends AbstractPlayerCommand {
        private final boolean on;

        Log(boolean on) {
            super(on ? "logon" : "logoff", on ? "cozyranch_produce.commands.poop.logon.desc" : "cozyranch_produce.commands.poop.logoff.desc");
            this.on = on;
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            PoopService s = ProduceFeature.poop();
            if (s != null) s.setLog(on);
            context.sendMessage(Message.translation(on ? "cozyranch_produce.poop.log.on" : "cozyranch_produce.poop.log.off"));
        }
    }
}
