package com.hexvane.cozytalesranching.housedisplay;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * {@code /ranch display status} lists, per house in the player's world that has display points, how many residents
 * are inside and which are shown (also logged, with each shown entity's model, scale and position, for server
 * tests). {@code /ranch display refresh} runs the check now. Admin only.
 */
public final class DisplayCommand extends AbstractCommandCollection {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    public DisplayCommand() {
        super("display", "cozyranch_display.commands.display.desc");
        this.requireNoPermission();
        this.addSubCommand(new StatusCommand());
        this.addSubCommand(new RefreshCommand());
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
            return "[" + model + " at " + at + (intangible ? " intangible" : "") + "]";
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
            context.sendMessage(Message.translation("cozyranch_display.refresh.done").param("shown", HouseDisplayService.shownCount()));
        }
    }
}
