package com.hexvane.cozytalesranching.prefs;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The mod's feedback and flavour chat lines, which a player can turn off in the journal's settings.
 *
 * <p>The rule: a line goes through {@link #tell} when it only reports what the player can already see happen (progress,
 * success, arrivals, discoveries). A line stays on the plain {@code sendMessage} (always shown) when the player needs it
 * to act or to understand why nothing happened: command replies, refusals and their reasons ("that belongs to someone
 * else", no permission, full, wrong food), confirmations the player must answer, anything from or about another player
 * (rehoming offers and their outcome), and information the player asked for (a trough's contents, a house summary).
 */
public final class RanchChat {
    private RanchChat() {}

    /** Sends a feedback or flavour line unless this player turned ranch chat off. */
    public static void tell(@Nullable PlayerRef player, @Nonnull Message message) {
        if (player != null && shows(player.getUuid())) player.sendMessage(message);
    }

    /** Whether a feedback or flavour line reaches this player. */
    public static boolean shows(@Nonnull UUID player) {
        return RanchPrefs.chat(player);
    }
}
