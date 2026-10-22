package com.hexvane.cozytalesranching.api.event;

import com.hexvane.cozytalesranching.api.AnimalView;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** An animal became a ranch animal: tamed by a player, or adopted from an animal tamed before this mod (vanilla or another listed role). */
public final class AnimalTamedEvent extends RanchAnimalEvent {
    public enum Source { TAMED, ADOPTED }

    /** the player who tamed or claimed it, null when adopted unclaimed */
    @Nullable private final UUID playerId;
    /** how it joined the ranch */
    @Nonnull private final Source source;

    public AnimalTamedEvent(@Nonnull AnimalView animal, @Nullable UUID playerId, @Nonnull Source source) {
        super(animal);
        this.playerId = playerId;
        this.source = source;
    }

    @Nullable
    public UUID getPlayerId() {
        return playerId;
    }

    @Nonnull
    public Source getSource() {
        return source;
    }
}
