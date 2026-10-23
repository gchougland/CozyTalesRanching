package com.hexvane.cozytalesranching.rehome;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.breeding.TraitRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * {@code /ranch rehome prep|hurt}: admin test tools for a player who cannot use entities. {@code prep} sets name,
 * hearts, happiness and traits on every animal following the admin; {@code hurt} makes the nearest creature
 * (not a player, not a ranch animal) within 16 blocks of each follower hit it, through the real damage pipeline.
 */
public final class RehomeDebugCommand extends AbstractCommandCollection {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    public RehomeDebugCommand() {
        super("rehome", "cozyranch_rehome.commands.debug.desc");
        this.requireNoPermission();
        this.addSubCommand(new PrepCommand());
        this.addSubCommand(new HurtCommand());
    }

    @Nonnull
    private static List<AnimalRecord> followers(@Nonnull PlayerRef player, @Nonnull World world) {
        List<AnimalRecord> out = new ArrayList<>();
        for (AnimalRecord r : Ranch.animals().ownedBy(player.getUuid())) {
            if (r.following && r.state == AnimalRecord.State.ACTIVE && world.getName().equals(r.worldName)) out.add(r);
        }
        return out;
    }

    private static final class PrepCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> nameArg;
        private final OptionalArg<Integer> heartsArg;
        private final OptionalArg<Double> happinessArg;
        private final OptionalArg<String> traitsArg;

        PrepCommand() {
            super("prep", "cozyranch_rehome.commands.debug.prep.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.nameArg = withOptionalArg("name", "cozyranch_rehome.commands.debug.prep.arg.name", ArgTypes.STRING);
            this.heartsArg = withOptionalArg("hearts", "cozyranch_rehome.commands.debug.prep.arg.hearts", ArgTypes.INTEGER);
            this.happinessArg = withOptionalArg("happiness", "cozyranch_rehome.commands.debug.prep.arg.happiness", ArgTypes.DOUBLE);
            this.traitsArg = withOptionalArg("traits", "cozyranch_rehome.commands.debug.prep.arg.traits", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            List<AnimalRecord> list = followers(playerRef, world);
            for (AnimalRecord r : list) {
                if (context.provided(nameArg)) {
                    String n = context.get(nameArg).trim();
                    r.name = n.isEmpty() || n.equals("-") ? null : n;
                }
                if (context.provided(heartsArg)) r.hearts = Math.clamp(context.get(heartsArg), 0, ButcherRules.MAX_HEARTS);
                if (context.provided(happinessArg)) r.happiness = (float) Math.clamp(context.get(happinessArg), 0.0, 1.0);
                if (context.provided(traitsArg)) {
                    r.traits = new ArrayList<>(Arrays.stream(context.get(traitsArg).split("[,+;]")).map(String::trim)
                        .filter(TraitRegistry.get()::has).toList());
                }
                Ranch.animals().markDirty(r);
                LOG.info("Rehome prep: " + RehomeAnimals.describe(r) + " hearts " + r.hearts + ", happiness " + r.happiness + ", traits " + r.traits + ", baby " + r.baby);
            }
            context.sendMessage(Message.translation("cozyranch_rehome.debug.prepped").param("count", list.size()));
        }
    }

    private static final class HurtCommand extends AbstractPlayerCommand {
        private static final double RADIUS = 16.0;
        private final OptionalArg<Double> amountArg;

        HurtCommand() {
            super("hurt", "cozyranch_rehome.commands.debug.hurt.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.amountArg = withOptionalArg("amount", "cozyranch_rehome.commands.debug.hurt.arg.amount", ArgTypes.DOUBLE);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            float amount = context.provided(amountArg) ? context.get(amountArg).floatValue() : 5f;
            int hit = 0;
            for (AnimalRecord r : followers(playerRef, world)) {
                Ref<EntityStore> animal = RehomeAnimals.live(world, r);
                Vector3d pos = animal == null ? null : RehomeAnimals.position(animal, store);
                if (pos == null) continue;
                Ref<EntityStore> attacker = nearestCreature(pos, animal, store);
                if (attacker == null) {
                    LOG.info("Rehome hurt: no creature within " + RADIUS + " blocks of " + RehomeAnimals.describe(r));
                    continue;
                }
                Damage damage = new Damage(new Damage.EntitySource(attacker), DamageCause.getAssetMap().getIndex("Physical"), amount);
                DamageSystems.executeDamage(animal, store, damage);
                NPCEntity npc = store.getComponent(attacker, NPCEntity.getComponentType());
                LOG.info("Rehome hurt: " + (npc != null ? npc.getRoleName() : "?") + " hits " + RehomeAnimals.describe(r) + " for " + amount + ", cancelled " + damage.isCancelled());
                hit++;
            }
            context.sendMessage(Message.translation("cozyranch_rehome.debug.hurt").param("count", hit));
        }

        @Nullable
        private static Ref<EntityStore> nearestCreature(@Nonnull Vector3d pos, @Nonnull Ref<EntityStore> self, @Nonnull Store<EntityStore> store) {
            Ref<EntityStore> best = null;
            double bestD = Double.MAX_VALUE;
            for (Ref<EntityStore> e : List.copyOf(TargetUtil.getAllEntitiesInSphere(pos, RADIUS, store))) {
                if (e.equals(self) || store.getComponent(e, Player.getComponentType()) != null) continue;
                NPCEntity npc = store.getComponent(e, NPCEntity.getComponentType());
                if (npc == null || npc.getRoleName() == null || npc.getRoleName().startsWith("CozyRanch_")) continue;
                Vector3d p = RehomeAnimals.position(e, store);
                double d = p == null ? Double.MAX_VALUE : p.distanceSquared(pos);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            return best;
        }
    }
}
