package com.hexvane.cozytalesranching.produce;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** {@code /ranch produce status|tick [minutes]|collect}: admin test tools for production, usable by the headless player. */
public final class ProduceCommand extends AbstractCommandCollection {
    static final double COLLECT_RADIUS = 32;

    public ProduceCommand() {
        super("produce", "cozyranch_produce.commands.produce.desc");
        this.requireNoPermission();
        this.addSubCommand(new Status());
        this.addSubCommand(new Tick());
        this.addSubCommand(new Collect());
        this.addSubCommand(new PoopCommand());
    }

    @Nonnull
    private static Message speciesName(@Nonnull String speciesId) {
        return SpeciesRegistry.byId(speciesId).map(s -> Message.translation(s.NameKey)).orElse(Message.raw(speciesId));
    }

    /** Every house of this world: producers, items waiting and the bucket products that wait for buckets. */
    static final class Status extends AbstractPlayerCommand {
        Status() {
            super("status", "cozyranch_produce.commands.produce.status.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            List<HouseRecord> houses = Ranch.houses().inWorld(world.getName());
            int cap = ProduceApi.cap();
            context.sendMessage(Message.translation("cozyranch_produce.status.header")
                .param("world", world.getName()).param("count", houses.size()).param("cap", cap));
            for (HouseRecord h : houses) {
                Map<String, Integer> items = ProduceApi.stored(h.houseId);
                long producers = Ranch.animals().inHouse(h.houseId).stream().filter(ProduceRules::produces).count();
                int total = ProduceApi.total(h.houseId);
                context.sendMessage(Message.translation("cozyranch_produce.status.house")
                    .param("species", speciesName(h.speciesId))
                    .param("x", h.x).param("y", h.y).param("z", h.z)
                    .param("producers", producers)
                    .param("total", total).param("cap", cap)
                    .param("items", ProduceService.describe(items)));
                int buckets = ProduceApi.waitingForBuckets(h);
                if (buckets > 0) context.sendMessage(Message.translation("cozyranch_produce.bucket.waiting").param("count", buckets));
                if (total >= cap) context.sendMessage(Message.translation("cozyranch_produce.house.full"));
            }
        }
    }

    /** {@code tick}: credits 60 minutes to every resident of this world now; {@code tick <minutes>} that many. */
    static final class Tick extends AbstractPlayerCommand {
        static final int DEFAULT_MINUTES = 60;

        Tick() {
            super("tick", "cozyranch_produce.commands.produce.tick.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.addUsageVariant(new TickMinutes());
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            run(context, world, DEFAULT_MINUTES);
        }

        static void run(@Nonnull CommandContext context, @Nonnull World world, int minutes) {
            ProduceService service = ProduceFeature.service();
            if (service == null) return;
            ProduceService.PassResult r = service.pass(world, System.currentTimeMillis(), Math.max(0, minutes));
            context.sendMessage(Message.translation("cozyranch_produce.tick.done")
                .param("asked", Math.max(0, minutes))
                .param("minutes", String.format(Locale.ROOT, "%.0f", r.minutes()))
                .param("items", r.items()).param("batches", r.animals()).param("houses", r.houses()));
        }
    }

    static final class TickMinutes extends AbstractPlayerCommand {
        private final RequiredArg<Integer> minutesArg;

        TickMinutes() {
            super("cozyranch_produce.commands.produce.tick.minutes.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.minutesArg = this.withRequiredArg("minutes", "cozyranch_produce.commands.produce.tick.arg.minutes", ArgTypes.INTEGER);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            Integer m = context.get(minutesArg);
            Tick.run(context, world, m == null ? Tick.DEFAULT_MINUTES : m);
        }
    }

    /** Collects every house within 32 blocks the player may collect from, as the house page's Collect button does. */
    static final class Collect extends AbstractPlayerCommand {
        Collect() {
            super("collect", "cozyranch_produce.commands.produce.collect.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t == null) return;
            Vector3d pos = t.getPosition();
            int found = 0;
            for (HouseRecord h : Ranch.houses().inWorld(world.getName())) {
                double dx = h.doorX - pos.x, dy = h.doorY - pos.y, dz = h.doorZ - pos.z;
                if (dx * dx + dy * dy + dz * dz > COLLECT_RADIUS * COLLECT_RADIUS) continue;
                found++;
                ProduceService.CollectResult r = ProduceApi.collect(ref, store, h);
                if (r.denied()) {
                    context.sendMessage(Message.translation("cozyranch_produce.collect.denied").param("species", speciesName(h.speciesId)));
                    continue;
                }
                context.sendMessage(Message.translation("cozyranch_produce.collect.done")
                    .param("species", speciesName(h.speciesId))
                    .param("moved", r.moved()).param("items", ProduceService.describe(r.movedItems())).param("left", r.left()));
                if (r.needBucket() > 0) {
                    context.sendMessage(Message.translation("cozyranch_produce.collect.bucket").param("count", r.needBucket()));
                }
            }
            if (found == 0) context.sendMessage(Message.translation("cozyranch_produce.collect.none").param("radius", (int) COLLECT_RADIUS));
        }
    }
}
