package com.hexvane.cozytalesranching.house;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseService.Result;
import com.hexvane.cozytalesranching.prefs.RanchChat;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Using a house: the assign gesture. Owned, unassigned animals of the house's species that follow the
 * player and are within {@link HouseRules#ASSIGN_RADIUS} move in, up to capacity. Otherwise (no followers, or none could
 * move in) the house page opens, saying why none moved in, or, while there is no page, a one-line summary. World thread, outside ticks.
 */
final class HouseUse {
    private HouseUse() {}

    static void use(@Nonnull World world, @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity, @Nonnull HouseRecord house) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        List<AnimalRecord> followers = nearbyFollowers(world, store, player, playerEntity, house);
        if (!followers.isEmpty()) {
            int moved = 0;
            Result last = Result.OK;
            for (AnimalRecord r : followers) {
                Result result = Ranch.houseService().assign(player, r, house);
                if (result == Result.OK) moved++;
                else last = result;
                if (result == Result.HOUSE_FULL) break;
            }
            int residents = Ranch.animals().inHouse(house.houseId).size();
            Decision decision = decide(followers.size(), moved, last);
            if (moved > 0) {
                RanchChat.tell(player, Message.translation("cozyranch_house.assign.done")
                    .param("count", moved).param("house", HouseBlocks.displayName(house))
                    .param("residents", residents).param("capacity", house.capacity));
            }
            if (decision.openPage()) {
                // nobody could move in (most often: the house is full); the page still opens and says why, followers keep following
                if (Ranch.housePages().open(playerEntity, store, house, decision.refusal())) return;
            }
            if (last == Result.NOT_OWNER && !player.getUuid().equals(house.ownerId) && HouseAccess.manage(player.getUuid(), house) == Result.OK) {
                // a claim lets this player look after the house, but animals only live in their own owner's houses
                player.sendMessage(Message.translation("cozyranch_house.assign.other_owner")
                    .param("house", HouseBlocks.displayName(house)).param("owner", HousePlacement.ownerName(house.ownerId)));
            } else if (last != Result.OK) {
                player.sendMessage(refusal(last, house, residents));
            }
            if (decision.openPage()) sendSummary(player, house);
            return;
        }
        if (Ranch.housePages().open(playerEntity, store, house)) return;
        sendSummary(player, house);
    }

    /**
     * What using a house does after trying to move the followers in. When at least one moved in, the gesture worked: chat
     * says so and no page opens. When followers came but none could move in (full, wrong owner ...), the house page opens
     * anyway with the reason as its notice, so a full house never locks its own page.
     */
    record Decision(boolean openPage, @javax.annotation.Nullable Result refusal) {}

    @Nonnull
    static Decision decide(int followers, int moved, @Nonnull Result last) {
        if (followers <= 0) return new Decision(true, null);
        if (moved > 0) return new Decision(false, null);
        return new Decision(true, last == Result.OK ? null : last);
    }

    static void sendSummary(@Nonnull PlayerRef player, @Nonnull HouseRecord house) {
        List<AnimalRecord> residents = Ranch.animals().inHouse(house.houseId);
        long inside = residents.stream().filter(r -> r.state == AnimalRecord.State.STORED).count();
        player.sendMessage(Message.translation("cozyranch_house.summary")
            .param("house", HouseBlocks.displayName(house))
            .param("owner", HousePlacement.ownerName(house.ownerId))
            .param("residents", residents.size()).param("capacity", house.capacity).param("inside", inside));
    }

    @Nonnull
    static Message refusal(@Nonnull Result result, @Nonnull HouseRecord house, int residents) {
        String key = switch (result) {
            case HOUSE_FULL -> "cozyranch_house.assign.full";
            case NO_PERMISSION -> "cozyranch_house.assign.no_permission";
            case NOT_OWNER -> "cozyranch_house.assign.not_owner";
            case WRONG_SPECIES -> "cozyranch_house.assign.wrong_species";
            default -> "cozyranch_house.assign.failed";
        };
        return Message.translation(key).param("house", HouseBlocks.displayName(house))
            .param("residents", residents).param("capacity", house.capacity);
    }

    /** Through the player's records, never an entity scan. */
    @Nonnull
    private static List<AnimalRecord> nearbyFollowers(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef player,
                                                     @Nonnull Ref<EntityStore> playerEntity, @Nonnull HouseRecord house) {
        TransformComponent pt = store.getComponent(playerEntity, TransformComponent.getComponentType());
        if (pt == null) return List.of();
        Vector3d pp = pt.getPosition();
        double max = HouseRules.ASSIGN_RADIUS * HouseRules.ASSIGN_RADIUS;
        List<AnimalRecord> out = new ArrayList<>();
        for (AnimalRecord r : Ranch.animals().ownedBy(player.getUuid())) {
            if (!r.following || r.houseId != null || r.state != AnimalRecord.State.ACTIVE) continue;
            if (!Objects.equals(r.speciesId, house.speciesId)) continue;
            Ref<EntityStore> ref = HouseResidents.live(world, r);
            if (ref == null) continue;
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t != null && t.getPosition().distanceSquared(pp) <= max) out.add(r);
        }
        return out;
    }
}
