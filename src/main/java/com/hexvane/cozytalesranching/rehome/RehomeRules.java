package com.hexvane.cozytalesranching.rehome;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Who may give, accept, move and process an animal. No engine types, so the rules are unit tested. */
final class RehomeRules {
    /** How long a transfer offer waits for the recipient. */
    static final long OFFER_TTL_MS = 60_000;

    enum Verdict {
        OK,
        NO_PERMISSION,
        NOT_OWNER,
        UNOWNED,
        SELF,
        BABY,
        RECIPIENT_FULL,
        OWNER_CHANGED
    }

    private RehomeRules() {}

    /**
     * Offering (or with {@code force}, transferring at once) an animal to another player. The owner needs
     * {@code cozyranch.transfer}; {@code force} needs {@code cozyranch.admin.manageany} and works on anyone's animal.
     */
    @Nonnull
    static Verdict checkGive(@Nonnull UUID actor, @Nullable UUID owner, @Nonnull UUID recipient,
                             boolean hasTransfer, boolean hasManageAny, boolean force) {
        if (force) {
            if (!hasManageAny) return Verdict.NO_PERMISSION;
            if (owner == null) return Verdict.UNOWNED;
            return recipient.equals(owner) ? Verdict.SELF : Verdict.OK;
        }
        if (!hasTransfer) return Verdict.NO_PERMISSION;
        if (owner == null) return Verdict.UNOWNED;
        if (!actor.equals(owner)) return Verdict.NOT_OWNER;
        return recipient.equals(owner) ? Verdict.SELF : Verdict.OK;
    }

    /**
     * Accepting an offer: the recipient needs {@code cozyranch.transfer} and room under the per-player limit, and
     * the giver must still own the animal (it may have been released or given away since).
     */
    @Nonnull
    static Verdict checkAccept(@Nonnull Offer offer, @Nullable UUID currentOwner, boolean recipientHasTransfer,
                               int recipientOwned, int maxPerPlayer) {
        if (!recipientHasTransfer) return Verdict.NO_PERMISSION;
        if (!Objects.equals(currentOwner, offer.fromId())) return Verdict.OWNER_CHANGED;
        if (recipientOwned >= maxPerPlayer) return Verdict.RECIPIENT_FULL;
        return Verdict.OK;
    }

    /**
     * Processing at the Butcher's Block: only the owner, never someone else's animal (not even for admins), never a
     * baby, and only with {@code cozyranch.butcher}.
     */
    @Nonnull
    static Verdict checkButcher(@Nonnull UUID actor, @Nullable UUID owner, boolean baby, boolean hasButcher) {
        if (!hasButcher) return Verdict.NO_PERMISSION;
        if (owner == null) return Verdict.UNOWNED;
        if (!actor.equals(owner)) return Verdict.NOT_OWNER;
        return baby ? Verdict.BABY : Verdict.OK;
    }

    static boolean isExpired(@Nonnull Offer offer, long nowMs) {
        return nowMs - offer.createdMs() >= OFFER_TTL_MS || nowMs < offer.createdMs();
    }

    /** One pending transfer offer. */
    record Offer(@Nonnull UUID animalId, @Nonnull UUID fromId, @Nonnull UUID toId, long createdMs) {}
}
