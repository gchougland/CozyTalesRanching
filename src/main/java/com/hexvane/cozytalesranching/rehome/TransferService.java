package com.hexvane.cozytalesranching.rehome;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.api.event.AnimalOwnerChangedEvent;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.house.HouseService;
import com.hexvane.cozytalesranching.rehome.RehomeRules.Offer;
import com.hexvane.cozytalesranching.rehome.RehomeRules.Verdict;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Giving an animal to another player: an offer the recipient accepts or declines within a minute, or an admin's
 * forced transfer. The transfer itself runs on the animal's world thread: out of its house, no longer following,
 * new owner, saved, then {@link AnimalOwnerChangedEvent}. Name, hearts, traits, personality and the rest stay.
 */
final class TransferService {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final OfferBook OFFERS = new OfferBook();

    private TransferService() {}

    /** {@code /ranch give}: offers the animal, or with {@code force} transfers it at once. */
    static void give(@Nonnull PlayerRef actor, @Nonnull AnimalRecord record, @Nonnull PlayerRef recipient, boolean force) {
        UUID id = actor.getUuid();
        Verdict v = RehomeRules.checkGive(id, record.ownerId, recipient.getUuid(),
            RanchPermissions.has(id, RanchPermissions.TRANSFER), RanchPermissions.has(id, RanchPermissions.ADMIN_MANAGE_ANY), force);
        Message animal = RehomeAnimals.displayName(record);
        if (v != Verdict.OK) {
            actor.sendMessage(Message.translation(giveKey(v)).param("animal", animal).param("player", recipient.getUsername()));
            return;
        }
        if (force) {
            onAnimalWorld(record, actor, () -> transfer(record, recipient, actor, true));
            return;
        }
        long now = System.currentTimeMillis();
        OFFERS.prune(now);
        OFFERS.put(new Offer(record.animalId, id, recipient.getUuid(), now));
        LOG.info("Transfer offer: " + actor.getUsername() + " offers " + RehomeAnimals.describe(record) + " to " + recipient.getUsername());
        actor.sendMessage(Message.translation("cozyranch_rehome.give.offered").param("animal", animal).param("player", recipient.getUsername()));
        recipient.sendMessage(Message.translation("cozyranch_rehome.give.incoming").param("animal", animal).param("player", actor.getUsername()));
    }

    /** {@code /ranch accept}. */
    static void accept(@Nonnull PlayerRef recipient) {
        Optional<Offer> taken = OFFERS.take(recipient.getUuid(), System.currentTimeMillis());
        if (taken.isEmpty()) {
            recipient.sendMessage(Message.translation("cozyranch_rehome.accept.none"));
            return;
        }
        Offer offer = taken.get();
        AnimalRecord record = Ranch.animals().get(offer.animalId()).orElse(null);
        if (record == null) {
            recipient.sendMessage(Message.translation("cozyranch_rehome.accept.gone"));
            return;
        }
        onAnimalWorld(record, recipient, () -> {
            UUID rid = recipient.getUuid();
            Verdict v = RehomeRules.checkAccept(offer, record.ownerId, RanchPermissions.has(rid, RanchPermissions.TRANSFER),
                Ranch.animals().ownedBy(rid).size(), RanchConfig.get().maxAnimalsPerPlayer());
            if (v != Verdict.OK) {
                recipient.sendMessage(Message.translation(acceptKey(v)).param("animal", RehomeAnimals.displayName(record)));
                return;
            }
            transfer(record, recipient, null, false);
        });
    }

    /** {@code /ranch decline}. */
    static void decline(@Nonnull PlayerRef recipient) {
        Optional<Offer> taken = OFFERS.take(recipient.getUuid(), System.currentTimeMillis());
        if (taken.isEmpty()) {
            recipient.sendMessage(Message.translation("cozyranch_rehome.accept.none"));
            return;
        }
        Offer offer = taken.get();
        Message animal = Ranch.animals().get(offer.animalId()).map(RehomeAnimals::displayName).orElse(Message.translation("cozyranch_rehome.animal.unknown"));
        recipient.sendMessage(Message.translation("cozyranch_rehome.decline.done").param("animal", animal));
        PlayerRef giver = Universe.get().getPlayer(offer.fromId());
        if (giver != null) giver.sendMessage(Message.translation("cozyranch_rehome.decline.told").param("animal", animal).param("player", recipient.getUsername()));
        LOG.info("Transfer offer of " + offer.animalId() + " declined by " + recipient.getUsername());
    }

    /** Runs {@code task} on the animal's world thread, or tells {@code who} the world is not loaded. */
    private static void onAnimalWorld(@Nonnull AnimalRecord record, @Nonnull PlayerRef who, @Nonnull Runnable task) {
        World world = Universe.get().getWorld(record.worldName);
        if (world == null) {
            who.sendMessage(Message.translation("cozyranch_rehome.world_unloaded").param("animal", RehomeAnimals.displayName(record)));
            return;
        }
        world.execute(task);
    }

    /**
     * Moves the animal to {@code recipient}. Its house is left through {@link HouseService#unassign}, acting as whoever
     * may: the forcing admin, the giver while they still own it, else the recipient once they do. Animal world thread.
     */
    private static void transfer(@Nonnull AnimalRecord record, @Nonnull PlayerRef recipient, @Nullable PlayerRef admin, boolean forced) {
        UUID previous = record.ownerId;
        Message animal = RehomeAnimals.displayName(record);
        if (record.houseId != null && !leaveHouse(record, previous, recipient, admin)) {
            record.ownerId = previous;
            Ranch.animals().markDirty(record);
            PlayerRef told = admin != null ? admin : recipient;
            told.sendMessage(Message.translation("cozyranch_rehome.transfer.house_refused").param("animal", animal));
            return;
        }
        record.ownerId = recipient.getUuid();
        record.following = false;
        World world = Universe.get().getWorld(record.worldName);
        if (world != null) {
            Ref<EntityStore> ref = RehomeAnimals.live(world, record);
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (ref != null) RehomeAnimals.stopFollowing(ref, store);
        }
        Ranch.animals().markDirty(record);
        LOG.info("Transfer " + (forced ? "(forced) " : "") + RehomeAnimals.describe(record) + ": owner " + previous + " -> "
            + recipient.getUuid() + " (" + recipient.getUsername() + "), hearts " + record.hearts + ", traits " + record.traits
            + ", personality " + record.personality + ", house " + record.houseId);
        recipient.sendMessage(Message.translation("cozyranch_rehome.transfer.received").param("animal", animal));
        PlayerRef giver = previous == null ? null : Universe.get().getPlayer(previous);
        if (giver != null) giver.sendMessage(Message.translation("cozyranch_rehome.transfer.given").param("animal", animal).param("player", recipient.getUsername()));
        if (admin != null && (giver == null || !admin.getUuid().equals(giver.getUuid()))) {
            admin.sendMessage(Message.translation("cozyranch_rehome.transfer.forced").param("animal", animal).param("player", recipient.getUsername()));
        }
        RanchEvents.fire(new AnimalOwnerChangedEvent(record.view(), previous, recipient.getUuid()));
    }

    private static boolean leaveHouse(@Nonnull AnimalRecord record, @Nullable UUID previous, @Nonnull PlayerRef recipient, @Nullable PlayerRef admin) {
        HouseService houses = Ranch.houseService();
        List<PlayerRef> before = new ArrayList<>();
        if (admin != null) before.add(admin);
        PlayerRef giver = previous == null ? null : Universe.get().getPlayer(previous);
        if (giver != null) before.add(giver);
        for (PlayerRef actor : before) {
            if (houses.unassign(actor, record) == HouseService.Result.OK) return true;
        }
        record.ownerId = recipient.getUuid();
        return houses.unassign(recipient, record) == HouseService.Result.OK;
    }

    @Nonnull
    private static String giveKey(@Nonnull Verdict v) {
        return switch (v) {
            case NOT_OWNER -> "cozyranch_rehome.give.not_owner";
            case UNOWNED -> "cozyranch_rehome.give.unowned";
            case SELF -> "cozyranch_rehome.give.self";
            default -> "cozyranch_rehome.give.no_permission";
        };
    }

    @Nonnull
    private static String acceptKey(@Nonnull Verdict v) {
        return switch (v) {
            case OWNER_CHANGED -> "cozyranch_rehome.accept.owner_changed";
            case RECIPIENT_FULL -> "cozyranch_rehome.accept.full";
            default -> "cozyranch_rehome.accept.no_permission";
        };
    }

    /** For tests and status: pending offers. */
    static int pending() {
        return OFFERS.size();
    }
}
