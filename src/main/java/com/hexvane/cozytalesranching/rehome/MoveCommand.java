package com.hexvane.cozytalesranching.rehome;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.house.HouseService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * {@code /ranch move <animal> <house name|nearest>}: moves an animal into another of its owner's houses of the same
 * species through {@link HouseService#assign} (same rules as the house page: owner, species, room). No walking needed:
 * an animal out and about walks to the new door, one asleep inside comes out of the new house in the morning.
 */
public final class MoveCommand extends AbstractPlayerCommand {
    static final String NEAREST = "nearest";
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private final RequiredArg<String> animalArg;
    private final RequiredArg<String> houseArg;

    public MoveCommand() {
        super("move", "cozyranch_rehome.commands.move.desc");
        this.setPermissionGroups("hytale:Adventurer");
        this.requirePermission(RanchPermissions.HOUSE_MANAGE);
        this.animalArg = withRequiredArg("animal", "cozyranch_rehome.commands.arg.animal", ArgTypes.STRING);
        this.houseArg = withRequiredArg("house", "cozyranch_rehome.commands.move.arg.house", ArgTypes.GREEDY_STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        boolean admin = RanchPermissions.has(playerRef.getUuid(), RanchPermissions.ADMIN_MANAGE_ANY);
        AnimalRecord record = RehomeAnimals.resolve(context.get(animalArg), playerRef, ref, store, world, admin).orElse(null);
        if (record == null) return;
        Message animal = RehomeAnimals.displayName(record);
        if (record.ownerId == null || (!record.ownerId.equals(playerRef.getUuid()) && !admin)) {
            playerRef.sendMessage(Message.translation("cozyranch_rehome.move.not_owner").param("animal", animal));
            return;
        }
        String wanted = context.get(houseArg).trim();
        List<HouseRecord> mine = Ranch.houses().ownedBy(record.ownerId).stream()
            .filter(h -> h.speciesId.equals(record.speciesId) && !Objects.equals(h.houseId, record.houseId))
            .toList();
        Vector3d at = RehomeAnimals.position(ref, store);
        HouseRecord house = wanted.equalsIgnoreCase(NEAREST) ? nearestWithRoom(mine, world.getName(), at) : named(mine, wanted);
        if (house == null) {
            playerRef.sendMessage(Message.translation(wanted.equalsIgnoreCase(NEAREST) ? "cozyranch_rehome.move.no_nearest" : "cozyranch_rehome.move.no_house")
                .param("animal", animal).param("house", wanted));
            return;
        }
        World houseWorld = Universe.get().getWorld(house.worldName);
        if (houseWorld == null) {
            playerRef.sendMessage(Message.translation("cozyranch_rehome.world_unloaded").param("animal", animal));
            return;
        }
        houseWorld.execute(() -> {
            HouseService.Result result = Ranch.houseService().assign(playerRef, record, house);
            LOG.info("Move " + RehomeAnimals.describe(record) + " to house " + house.houseId + " (" + house.name + ") by " + playerRef.getUsername() + ": " + result);
            playerRef.sendMessage(Message.translation(resultKey(result)).param("animal", animal).param("house", houseName(house)));
        });
    }

    @Nullable
    static HouseRecord nearestWithRoom(@Nonnull List<HouseRecord> houses, @Nonnull String worldName, @Nullable Vector3d at) {
        HouseRecord best = null;
        double bestD = Double.MAX_VALUE;
        for (HouseRecord h : houses) {
            if (!h.worldName.equals(worldName) || Ranch.animals().inHouse(h.houseId).size() >= h.capacity) continue;
            double d = at == null ? 0 : sq(h.doorX - at.x) + sq(h.doorY - at.y) + sq(h.doorZ - at.z);
            if (d < bestD) {
                bestD = d;
                best = h;
            }
        }
        return best;
    }

    /** By name (case-insensitive), else by the start of its id. */
    @Nullable
    static HouseRecord named(@Nonnull List<HouseRecord> houses, @Nonnull String wanted) {
        for (HouseRecord h : houses) if (h.name != null && h.name.equalsIgnoreCase(wanted)) return h;
        String lower = wanted.toLowerCase(Locale.ROOT);
        if (lower.length() < 4) return null;
        HouseRecord found = null;
        for (HouseRecord h : houses) {
            if (!h.houseId.toString().startsWith(lower)) continue;
            if (found != null) return null;
            found = h;
        }
        return found;
    }

    @Nonnull
    private static String houseName(@Nonnull HouseRecord h) {
        return h.name != null && !h.name.isBlank() ? h.name : h.houseId.toString().substring(0, 8);
    }

    @Nonnull
    private static String resultKey(@Nonnull HouseService.Result r) {
        return switch (r) {
            case OK -> "cozyranch_rehome.move.done";
            case NOT_OWNER -> "cozyranch_rehome.move.not_owner";
            case NO_PERMISSION -> "cozyranch_rehome.move.no_permission";
            case WRONG_SPECIES -> "cozyranch_rehome.move.wrong_species";
            case HOUSE_FULL -> "cozyranch_rehome.move.full";
            case NOT_FOUND, UNAVAILABLE -> "cozyranch_rehome.move.failed";
        };
    }

    private static double sq(double v) {
        return v * v;
    }
}
