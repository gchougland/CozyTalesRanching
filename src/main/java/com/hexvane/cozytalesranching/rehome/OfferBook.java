package com.hexvane.cozytalesranching.rehome;

import com.hexvane.cozytalesranching.rehome.RehomeRules.Offer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pending transfer offers, memory only (a restart forgets them, as a minute-long offer should). One offer per
 * recipient (a newer one replaces it) and one per animal (offering it again to someone else moves the offer).
 */
final class OfferBook {
    private final Map<UUID, Offer> byRecipient = new HashMap<>();

    /** Returns the offer it replaced for this recipient, if any. */
    @Nullable
    synchronized Offer put(@Nonnull Offer offer) {
        byRecipient.values().removeIf(o -> o.animalId().equals(offer.animalId()));
        return byRecipient.put(offer.toId(), offer);
    }

    /** Removes and returns the recipient's offer if it has not expired; an expired one is dropped. */
    @Nonnull
    synchronized Optional<Offer> take(@Nonnull UUID recipient, long nowMs) {
        Offer o = byRecipient.remove(recipient);
        if (o == null || RehomeRules.isExpired(o, nowMs)) return Optional.empty();
        return Optional.of(o);
    }

    @Nonnull
    synchronized Optional<Offer> peek(@Nonnull UUID recipient, long nowMs) {
        Offer o = byRecipient.get(recipient);
        if (o == null || RehomeRules.isExpired(o, nowMs)) return Optional.empty();
        return Optional.of(o);
    }

    /** Drops expired offers; returns them so their givers can be told. */
    @Nonnull
    synchronized java.util.List<Offer> prune(long nowMs) {
        java.util.List<Offer> gone = byRecipient.values().stream().filter(o -> RehomeRules.isExpired(o, nowMs)).toList();
        byRecipient.values().removeIf(o -> RehomeRules.isExpired(o, nowMs));
        return gone;
    }

    synchronized int size() {
        return byRecipient.size();
    }
}
