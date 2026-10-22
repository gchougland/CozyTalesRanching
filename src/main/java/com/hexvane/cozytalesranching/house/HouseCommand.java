package com.hexvane.cozytalesranching.house;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleMatch;
import com.hexvane.cozytalesranching.taming.TamingApi;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockRotation;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockPlaceUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * {@code /ranch house list} and {@code /ranch house debug place|use|claim|nightfall|morning}: admin tools
 * to inspect houses and to drive the house day from a console or a test player who cannot use blocks.
 */
public final class HouseCommand extends AbstractCommandCollection {
    public HouseCommand() {
        super("house", "cozyranch_house.commands.house.desc");
        this.requireNoPermission();
        this.addSubCommand(new ListCommand());
        this.addSubCommand(new DebugCommand());
    }

    private static final class DebugCommand extends AbstractCommandCollection {
        DebugCommand() {
            super("debug", "cozyranch_house.commands.debug.desc");
            this.requireNoPermission();
            this.addSubCommand(new PlaceCommand());
            this.addSubCommand(new UseCommand());
            this.addSubCommand(new ClaimCommand());
            this.addSubCommand(new NightfallCommand());
            this.addSubCommand(new MorningCommand());
        }
    }

    /** Every house in the player's world, with residents. */
    private static final class ListCommand extends AbstractPlayerCommand {
        ListCommand() {
            super("list", "cozyranch_house.commands.list.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            List<HouseRecord> houses = Ranch.houses().inWorld(world.getName());
            context.sendMessage(Message.translation("cozyranch_house.list.header").param("count", houses.size()).param("world", world.getName()));
            for (HouseRecord h : houses) {
                List<AnimalRecord> residents = Ranch.animals().inHouse(h.houseId);
                long inside = residents.stream().filter(r -> r.state == AnimalRecord.State.STORED).count();
                context.sendMessage(Message.translation("cozyranch_house.list.line")
                    .param("id", h.houseId.toString())
                    .param("house", HouseBlocks.displayName(h))
                    .param("owner", HousePlacement.ownerName(h.ownerId))
                    .param("x", h.x).param("y", h.y).param("z", h.z).param("rotation", h.rotation)
                    .param("door", String.format(Locale.ROOT, "%.1f %.1f %.1f", h.doorX, h.doorY, h.doorZ))
                    .param("residents", residents.size()).param("capacity", h.capacity).param("inside", inside)
                    .param("morning", h.lastMorningDay));
                for (AnimalRecord r : residents) {
                    context.sendMessage(Message.translation("cozyranch_house.list.resident")
                        .param("id", r.animalId.toString()).param("animal", TamingApi.displayName(r))
                        .param("state", r.state.name()).param("generation", r.generation));
                }
            }
        }
    }

    /** Places a species' house block at the player's feet through the engine's own placement, which fires the place event. */
    private static final class PlaceCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> speciesArg;
        private final DefaultArg<Integer> rotationArg;

        PlaceCommand() {
            super("place", "cozyranch_house.commands.place.desc");
            this.requirePermission(RanchPermissions.ADMIN);
            this.speciesArg = this.withRequiredArg("species", "cozyranch_house.commands.place.arg.species", ArgTypes.STRING);
            this.rotationArg = this.withDefaultArg("rotation", "cozyranch_house.commands.place.arg.rotation", ArgTypes.INTEGER, 0, "0");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String wanted = context.get(speciesArg);
            Optional<SpeciesDefinition> species = SpeciesRegistry.all().stream().filter(s -> s.Id.equalsIgnoreCase(wanted)).findFirst();
            if (species.isEmpty() || species.get().HouseBlock.isEmpty()) {
                context.sendMessage(Message.translation("cozyranch_house.debug.unknown_species").param("species", wanted));
                return;
            }
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
            if (t == null || hotbar == null) return;
            Vector3d p = t.getPosition();
            Vector3i pos = new Vector3i((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
            Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
            if (section == null || !section.isValid()) return;
            String blockId = species.get().HouseBlock;
            Integer rot = context.get(rotationArg);
            Rotation yaw = Rotation.NORMAL[Math.floorMod(rot == null ? 0 : rot, 4)];
            boolean placed = BlockPlaceUtils.placeBlock(ref, new ItemStack(blockId, 1), blockId, hotbar.getInventory(), new Vector3i(0, 1, 0), pos,
                new BlockRotation(yaw.toPacket(), Rotation.None.toPacket(), Rotation.None.toPacket()), (byte) 0, false,
                section, world.getChunkStore().getStore(), store, false, false, false);
            context.sendMessage(Message.translation(placed ? "cozyranch_house.debug.placed" : "cozyranch_house.debug.place_failed")
                .param("block", blockId).param("x", pos.x).param("y", pos.y).param("z", pos.z));
        }
    }

    /** Does what using the nearest house (within 8 blocks of its door) does. */
    private static final class UseCommand extends AbstractPlayerCommand {
        UseCommand() {
            super("use", "cozyranch_house.commands.use.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            HouseRecord house = nearestHouse(world, store, ref);
            if (house == null) {
                context.sendMessage(Message.translation("cozyranch_house.debug.no_house"));
                return;
            }
            HouseUse.use(world, playerRef, ref, house);
        }
    }

    /**
     * Makes the nearest ranch animal within 8 blocks yours and has it follow you: an unowned record is
     * claimed, a ranch-role entity with no record (from {@code /npc spawn CozyRanch_Cow}) gets one.
     */
    private static final class ClaimCommand extends AbstractPlayerCommand {
        ClaimCommand() {
            super("claim", "cozyranch_house.commands.claim.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t == null) return;
            Vector3d p = new Vector3d(t.getPosition());
            Ref<EntityStore> best = null;
            RoleMatch bestMatch = null;
            double bestD = Double.MAX_VALUE;
            for (Ref<EntityStore> e : List.copyOf(TargetUtil.getAllEntitiesInSphere(p, HouseRules.ASSIGN_RADIUS, store))) {
                NPCEntity npc = store.getComponent(e, NPCEntity.getComponentType());
                if (npc == null || npc.getRoleName() == null) continue;
                RoleMatch m = SpeciesRegistry.byRole(npc.getRoleName()).orElse(null);
                if (m == null || m.kind() == RoleKind.WILD || m.kind() == RoleKind.WILD_BABY) continue;
                UUIDComponent uuid = store.getComponent(e, UUIDComponent.getComponentType());
                AnimalRecord existing = uuid == null ? null : Ranch.animals().byEntity(uuid.getUuid()).orElse(null);
                if (existing != null && existing.ownerId != null && !existing.ownerId.equals(playerRef.getUuid())) continue;
                TransformComponent et = store.getComponent(e, TransformComponent.getComponentType());
                double d = et == null ? Double.MAX_VALUE : et.getPosition().distanceSquared(p);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                    bestMatch = m;
                }
            }
            if (best == null) {
                context.sendMessage(Message.translation("cozyranch_house.debug.no_animal"));
                return;
            }
            UUIDComponent uuid = store.getComponent(best, UUIDComponent.getComponentType());
            if (uuid == null) return;
            AnimalRecord r = claim(world, store, best, uuid.getUuid(), bestMatch, playerRef.getUuid());
            context.sendMessage(Message.translation("cozyranch_house.debug.claimed").param("id", r.animalId.toString()).param("animal", TamingApi.displayName(r)));
        }

        @Nonnull
        private static AnimalRecord claim(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> entity, @Nonnull UUID entityUuid,
                                          @Nonnull RoleMatch match, @Nonnull UUID owner) {
            boolean baby = match.kind() == RoleKind.RANCH_BABY || match.kind() == RoleKind.ADOPT_BABY;
            AnimalRecord r = Ranch.animals().byEntity(entityUuid).orElse(null);
            if (r == null) {
                r = Ranch.animals().create(match.species().Id, owner, world.getName(), baby);
                r.tamedAtMs = System.currentTimeMillis();
                Ranch.animals().bindEntity(r, entity, entityUuid, store);
            }
            r.ownerId = owner;
            r.following = true;
            Ranch.animals().markDirty(r);
            if (match.kind() == RoleKind.ADOPT || match.kind() == RoleKind.ADOPT_BABY) {
                NPCEntity npc = store.getComponent(entity, NPCEntity.getComponentType());
                String role = match.species().ranchRoleFor(npc != null ? npc.getRoleName() : null, baby);
                if (!role.isEmpty()) TamingApi.swapRole(entity, role, null, store);
            }
            return r;
        }
    }

    private static final class NightfallCommand extends AbstractPlayerCommand {
        NightfallCommand() {
            super("nightfall", "cozyranch_house.commands.nightfall.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            HouseDaySystem.forceNight(world.getName());
            context.sendMessage(Message.translation("cozyranch_house.debug.nightfall").param("world", world.getName()));
        }
    }

    private static final class MorningCommand extends AbstractPlayerCommand {
        MorningCommand() {
            super("morning", "cozyranch_house.commands.morning.desc");
            this.requirePermission(RanchPermissions.ADMIN);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            HouseDaySystem.forceMorning(world.getName(), Ranch.houses().inWorld(world.getName()));
            context.sendMessage(Message.translation("cozyranch_house.debug.morning").param("world", world.getName()));
        }
    }

    @Nullable
    private static HouseRecord nearestHouse(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (t == null) return null;
        Vector3d p = t.getPosition();
        HouseRecord best = null;
        double bestD = HouseRules.ASSIGN_RADIUS * HouseRules.ASSIGN_RADIUS;
        for (HouseRecord h : Ranch.houses().inWorld(world.getName())) {
            double dx = h.doorX - p.x, dz = h.doorZ - p.z;
            double d = dx * dx + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = h;
            }
        }
        return best;
    }
}
