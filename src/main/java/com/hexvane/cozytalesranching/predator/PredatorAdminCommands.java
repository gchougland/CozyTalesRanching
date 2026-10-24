package com.hexvane.cozytalesranching.predator;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.predator.CalmBook.Entry;
import com.hexvane.cozytalesranching.taming.AnimalInteractions;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Test commands under {@code /ranch admin} (admin node), for a test player that cannot press Use on blocks or animals:
 * {@code bait <item>} fills the nearest Bait Bowl within 6 blocks through the real fill code (the item is not taken);
 * {@code feed <item>} offers food by hand to the nearest wild animal within 16 blocks through the real taming code;
 * {@code calm} lists the lured, calm and spooked predators of this world.
 */
public final class PredatorAdminCommands {
    private static final int BOWL_SEARCH = 6;
    private static final double FEED_RADIUS = 16;

    private PredatorAdminCommands() {}

    public static final class Bait extends AbstractPlayerCommand {
        private final RequiredArg<String> item;

        public Bait() {
            super("bait", "cozyranch_predator.commands.bait.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.item = withRequiredArg("item", "cozyranch_predator.commands.bait.arg", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            Vector3i bowl = nearestBowl(world, store, ref);
            if (bowl == null) {
                context.sendMessage(Message.translation("cozyranch_predator.admin.no_bowl").param("radius", BOWL_SEARCH));
                return;
            }
            boolean ok = BaitBowlInteraction.use(world, playerRef, bowl, item.get(context), false, true);
            context.sendMessage(Message.translation(ok ? "cozyranch_predator.admin.baited" : "cozyranch_predator.admin.not_baited")
                .param("x", bowl.x).param("y", bowl.y).param("z", bowl.z));
        }
    }

    public static final class Feed extends AbstractPlayerCommand {
        private final RequiredArg<String> item;

        public Feed() {
            super("feed", "cozyranch_predator.commands.feed.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.item = withRequiredArg("item", "cozyranch_predator.commands.feed.arg", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String species = AnimalInteractions.debugFeedNearest(playerRef, ref, world, store, FEED_RADIUS, item.get(context));
            context.sendMessage(species == null
                ? Message.translation("cozyranch_predator.admin.feed.none")
                : Message.translation("cozyranch_predator.admin.feed.done").param("species", species));
        }
    }

    public static final class Calm extends AbstractPlayerCommand {
        public Calm() {
            super("calm", "cozyranch_predator.commands.calm.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            Predators p = Predators.get();
            if (p == null) return;
            TransformComponent me = store.getComponent(ref, TransformComponent.getComponentType());
            var here = p.book.inWorld(world.getName());
            context.sendMessage(Message.translation("cozyranch_predator.admin.calm.header").param("count", here.size()).param("bowls", p.bowls.inWorld(world.getName()).size()));
            long now = System.currentTimeMillis();
            for (Entry e : here) {
                Ref<EntityStore> animal = world.getEntityStore().getRefFromUUID(e.entity);
                String state = "-";
                String distance = "-";
                String at = "-";
                String role = "-";
                if (animal != null && animal.isValid()) {
                    com.hypixel.hytale.server.npc.entities.NPCEntity npc = store.getComponent(animal, com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
                    if (npc != null && npc.getRoleName() != null) role = npc.getRoleName();
                    StateSupport s = store.getComponent(animal, StateSupport.getComponentType());
                    if (s != null) state = s.getStateName();
                    TransformComponent t = store.getComponent(animal, TransformComponent.getComponentType());
                    if (t != null && me != null) distance = String.format("%.1f", t.getPosition().distance(me.getPosition()));
                    if (t != null) at = String.format("%.1f %.1f %.1f", t.getPosition().x, t.getPosition().y, t.getPosition().z);
                }
                context.sendMessage(Message.translation("cozyranch_predator.admin.calm.line")
                    .param("species", e.speciesId).param("phase", e.phase.name()).param("seconds", Math.max(0, (e.untilMs - now) / 1000))
                    .param("state", state).param("distance", distance).param("at", at).param("role", role).param("id", e.entity.toString()));
            }
        }
    }

    /** The nearest Bait Bowl block within {@link #BOWL_SEARCH} blocks, from loaded sections only. A one-off command scan. */
    @Nullable
    static Vector3i nearestBowl(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> player) {
        TransformComponent t = store.getComponent(player, TransformComponent.getComponentType());
        if (t == null) return null;
        Vector3d p = t.getPosition();
        int px = (int) Math.floor(p.x), py = (int) Math.floor(p.y), pz = (int) Math.floor(p.z);
        Vector3i best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dx = -BOWL_SEARCH; dx <= BOWL_SEARCH; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -BOWL_SEARCH; dz <= BOWL_SEARCH; dz++) {
                    int d = dx * dx + dy * dy + dz * dz;
                    if (d >= bestD) continue;
                    if (BaitBowls.isBowl(BaitBowls.typeAt(world, px + dx, py + dy, pz + dz))) {
                        best = new Vector3i(px + dx, py + dy, pz + dz);
                        bestD = d;
                    }
                }
            }
        }
        return best;
    }
}
