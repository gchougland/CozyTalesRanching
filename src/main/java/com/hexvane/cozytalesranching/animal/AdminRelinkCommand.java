package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleMatch;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * {@code /ranch admin relink [--radius=48]}: repair for worlds where ranch animals came back untagged (a vanilla coop
 * re-created them). Every untagged ranch or adopt animal within the radius is matched to a MISSING record of the same
 * species and role last seen nearby and becomes that animal again, name and all. Then any MISSING animal in this world
 * that is still not back is respawned beside the admin. One sphere query when the command runs; nothing per tick.
 */
final class AdminRelinkCommand extends AbstractPlayerCommand {
    static final double DEFAULT_RADIUS = 48.0;
    private final OptionalArg<Double> radiusArg;

    AdminRelinkCommand() {
        super("relink", "cozyranch_animal.commands.admin.relink.desc");
        this.requirePermission(RanchPermissions.ADMIN_MANAGE_ANY);
        this.radiusArg = this.withOptionalArg("radius", "cozyranch_animal.commands.admin.relink.arg.radius", ArgTypes.DOUBLE);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        PersistentAnimalRegistry registry = AnimalFeature.registry();
        ComponentType<EntityStore, AnimalMarker> markerType = AnimalMarker.type();
        AnimalRecovery recovery = AnimalFeature.recovery();
        TransformComponent self = store.getComponent(ref, TransformComponent.getComponentType());
        if (registry == null || markerType == null || recovery == null || self == null) return;
        double radius = context.provided(radiusArg) ? Math.clamp(context.get(radiusArg), 1.0, 128.0) : DEFAULT_RADIUS;
        Vector3d here = new Vector3d(self.getPosition());

        int relinked = 0;
        for (Ref<EntityStore> e : new ArrayList<>(TargetUtil.getAllEntitiesInSphere(here, radius, store))) {
            if (e == null || !e.isValid() || store.getComponent(e, markerType) != null) continue;
            NPCEntity npc = store.getComponent(e, NPCEntity.getComponentType());
            UUIDComponent uuid = store.getComponent(e, UUIDComponent.getComponentType());
            TransformComponent t = store.getComponent(e, TransformComponent.getComponentType());
            if (npc == null || npc.getRoleName() == null || uuid == null || t == null) continue;
            RoleMatch match = SpeciesRegistry.byRole(npc.getRoleName()).orElse(null);
            if (match == null) continue;
            boolean foreign = com.hexvane.cozytalesranching.compat.CompatGuard.hasIgnoredComponent(store.getArchetype(e));
            if (!AnimalEntitySystem.mayBeOrphan(match.kind(), foreign, npc.getRoleName())) continue;
            if (AnimalEntitySystem.relink(registry, markerType, e, store, world, uuid.getUuid(), npc.getRoleName(), match, t.getPosition())) relinked++;
        }

        int respawned = 0, stillMissing = 0;
        List<AnimalRecord> missing = registry.store().inWorld(world.getName()).stream().filter(r -> r.state == AnimalRecord.State.MISSING).toList();
        for (AnimalRecord r : missing) {
            if (recovery.resummon(world, r, here)) respawned++;
            else stillMissing++;
        }
        context.sendMessage(Message.translation("cozyranch_animal.admin.relink.done")
            .param("relinked", relinked).param("respawned", respawned).param("missing", stillMissing));
    }
}
