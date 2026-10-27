package com.hexvane.cozytalesranching.housedisplay;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.NonTicking;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * {@code /ranch display status} lists, per house in the player's world that has display points, how many residents
 * are inside and which are shown (also logged, with each shown entity's model, scale and position, for server
 * tests). {@code /ranch display refresh} runs the check now. {@code /ranch display freeze|thaw} stops or restarts
 * ticking in the player's chunk, as the game does when no player is near (a test fixture). Admin only.
 */
public final class DisplayCommand extends AbstractCommandCollection {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    public DisplayCommand() {
        super("display", "cozyranch_display.commands.display.desc");
        this.requireNoPermission();
        this.addSubCommand(new StatusCommand());
        this.addSubCommand(new RefreshCommand());
        this.addSubCommand(new TickingCommand("freeze", true));
        this.addSubCommand(new TickingCommand("thaw", false));
    }

    private static final class StatusCommand extends AbstractPlayerCommand {
        StatusCommand() {
            super("status", "cozyranch_display.commands.status.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            int houses = 0;
            for (HouseRecord h : Ranch.houses().inWorld(world.getName())) {
                List<HouseDisplayData.Point> points = HouseDisplayData.get().points(h.blockId);
                List<HouseDisplayService.Shown> shown = HouseDisplayService.shown(h.houseId);
                if (points.isEmpty() && shown.isEmpty()) continue;
                houses++;
                long inside = Ranch.animals().inHouse(h.houseId).stream().filter(r -> r.state == AnimalRecord.State.STORED).count();
                StringBuilder detail = new StringBuilder();
                StringBuilder models = new StringBuilder();
                for (HouseDisplayService.Shown s : shown) {
                    String d = describe(store, s);
                    detail.append(' ').append(d);
                    if (models.length() > 0) models.append(", ");
                    models.append(d);
                }
                LOG.info("House display status: house " + h.houseId + " " + h.blockId + " rotation " + h.rotation + " points " + points.size()
                    + " inside " + inside + " shown " + shown.size() + detail);
                context.sendMessage(Message.translation("cozyranch_display.status.line")
                    .param("house", h.blockId).param("x", h.x).param("y", h.y).param("z", h.z)
                    .param("shown", shown.size()).param("points", points.size()).param("inside", inside)
                    .param("models", models.length() == 0 ? "-" : models.toString()));
            }
            if (houses == 0) context.sendMessage(Message.translation("cozyranch_display.status.none"));
            LOG.info("House display status: " + houses + " house(s) with points in " + world.getName() + ", " + HouseDisplayService.shownCount() + " shown in all worlds");
        }

        @Nonnull
        private static String describe(@Nonnull Store<EntityStore> store, @Nonnull HouseDisplayService.Shown s) {
            if (!s.ref().isValid()) return "[" + s.animalId() + " gone]";
            ModelComponent m = store.getComponent(s.ref(), ModelComponent.getComponentType());
            TransformComponent t = store.getComponent(s.ref(), TransformComponent.getComponentType());
            boolean intangible = store.getArchetype(s.ref()).contains(Intangible.getComponentType());
            String model = m != null && m.getModel() != null ? m.getModel().getModelAssetId() + " x" + String.format(Locale.ROOT, "%.2f", m.getModel().getScale()) : "?";
            String at = t != null ? String.format(Locale.ROOT, "%.2f %.2f %.2f yaw %.0f", t.getPosition().x, t.getPosition().y, t.getPosition().z, Math.toDegrees(t.getRotation().yaw())) : "?";
            int attachments = m != null && m.getModel() != null && m.getModel().getAttachments() != null ? m.getModel().getAttachments().length : 0;
            // a resident's skin (its model attachments), named only when it has one
            return "[" + model + " at " + at + (intangible ? " intangible" : "") + (attachments > 0 ? " +" + attachments + " attachments" : "") + "]";
        }
    }

    private static final class RefreshCommand extends AbstractPlayerCommand {
        RefreshCommand() {
            super("refresh", "cozyranch_display.commands.refresh.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            HouseDisplayService.pass(world);
            // every display entity in this world, tracked or not (for server tests: no stray copies)
            int entities = store.getEntityCountFor(DisplayMarker.getComponentType());
            LOG.info("House display refresh: " + HouseDisplayService.shownCount() + " shown, " + entities + " display entities in " + world.getName());
            context.sendMessage(Message.translation("cozyranch_display.refresh.done").param("shown", HouseDisplayService.shownCount()).param("entities", entities));
        }
    }

    /**
     * Marks the player's chunk and its sections non-ticking, or ticking again, the way the game does when no player
     * is near: entities in it are parked, and come back when it ticks again. While the player stays, the game does
     * not undo it (it only wakes sections that newly come into range).
     */
    private static final class TickingCommand extends AbstractPlayerCommand {
        private final boolean freeze;

        TickingCommand(@Nonnull String name, boolean freeze) {
            super(name, "cozyranch_display.commands." + name + ".desc");
            this.freeze = freeze;
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t == null) return;
            ChunkStore chunks = world.getChunkStore();
            Ref<ChunkStore> column = chunks.getChunkReference(ChunkUtil.indexChunkFromBlock(t.getPosition().x, t.getPosition().z));
            if (column == null || !column.isValid()) return;
            Store<ChunkStore> chunkStore = chunks.getStore();
            ComponentType<ChunkStore, NonTicking<ChunkStore>> nonTicking = ChunkStore.REGISTRY.getNonTickingComponentType();
            int cx = ChunkUtil.chunkCoordinate(t.getPosition().x), cz = ChunkUtil.chunkCoordinate(t.getPosition().z);
            int n = 0;
            // sections from the bottom up, until the first one that is not there
            for (int cy = 0; ; cy++) {
                Ref<ChunkStore> section = chunks.getChunkSectionReference(cx, cy, cz);
                if (section == null || !section.isValid()) break;
                if (freeze) chunkStore.ensureComponent(section, nonTicking);
                else chunkStore.tryRemoveComponent(section, nonTicking);
                n++;
            }
            if (freeze) chunkStore.ensureComponent(column, nonTicking);
            else chunkStore.tryRemoveComponent(column, nonTicking);
            context.sendMessage(Message.translation("cozyranch_display." + (freeze ? "freeze" : "thaw") + ".done").param("sections", n));
        }
    }
}
