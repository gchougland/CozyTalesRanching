package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * {@code /ranch admin hurt [--by=player|fall] [--amount=N] [--radius=R]}: hits every ranch-tagged animal within the radius
 * through the real damage pipeline, as the admin would with a weapon ({@code player}, the default) or as a fall would
 * ({@code fall}), and logs per animal whether the hit was cancelled and its health after. A test player cannot swing at
 * an entity, so this is how a server test proves the protection.
 */
final class AdminHurtCommand extends AbstractPlayerCommand {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private final OptionalArg<String> byArg;
    private final OptionalArg<Double> amountArg;
    private final OptionalArg<Double> radiusArg;

    AdminHurtCommand() {
        super("hurt", "cozyranch_animal.commands.admin.hurt.desc");
        this.requirePermission(RanchPermissions.ADMIN);
        this.byArg = withOptionalArg("by", "cozyranch_animal.commands.admin.hurt.arg.by", ArgTypes.STRING);
        this.amountArg = withOptionalArg("amount", "cozyranch_animal.commands.admin.hurt.arg.amount", ArgTypes.DOUBLE);
        this.radiusArg = withOptionalArg("radius", "cozyranch_animal.commands.admin.hurt.arg.radius", ArgTypes.DOUBLE);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String by = context.provided(byArg) ? context.get(byArg).toLowerCase(Locale.ROOT) : "player";
        float amount = context.provided(amountArg) ? context.get(amountArg).floatValue() : 1000f;
        double radius = context.provided(radiusArg) ? context.get(radiusArg) : 24.0;
        TransformComponent pt = store.getComponent(ref, TransformComponent.getComponentType());
        if (pt == null) return;
        Vector3d p = new Vector3d(pt.getPosition());
        var markerType = AnimalMarker.type();
        int hit = 0, survived = 0;
        for (Ref<EntityStore> e : List.copyOf(TargetUtil.getAllEntitiesInSphere(p, radius, store))) {
            if (markerType == null || store.getComponent(e, markerType) == null) continue;
            NPCEntity npc = store.getComponent(e, NPCEntity.getComponentType());
            UUIDComponent id = store.getComponent(e, UUIDComponent.getComponentType());
            if (npc == null || id == null) continue;
            Damage damage = by.equals("fall")
                ? new Damage(Damage.NULL_SOURCE, DamageCause.getAssetMap().getIndex("Fall"), amount)
                : new Damage(new Damage.EntitySource(ref), DamageCause.getAssetMap().getIndex("Physical"), amount);
            DamageSystems.executeDamage(e, store, damage);
            float health = health(e, store);
            boolean alive = health > 0;
            if (alive) survived++;
            hit++;
            AnimalRecord r = Ranch.animals().byEntity(id.getUuid()).orElse(null);
            LOG.info(String.format(Locale.ROOT, "Ranch hurt (%s, %.0f): %s %s owner %s cancelled %s health %.0f%s", by, amount, npc.getRoleName(), id.getUuid(),
                r == null ? "?" : String.valueOf(r.ownerId), damage.isCancelled(), health, alive ? "" : " KILLED"));
        }
        context.sendMessage(Message.translation("cozyranch_animal.admin.hurt.done").param("hit", hit).param("survived", survived));
    }

    private static float health(@Nonnull Ref<EntityStore> e, @Nonnull Store<EntityStore> store) {
        if (!e.isValid()) return 0;
        EntityStatMap stats = store.getComponent(e, EntityStatMap.getComponentType());
        EntityStatValue v = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        return v == null ? 0 : v.get();
    }
}
