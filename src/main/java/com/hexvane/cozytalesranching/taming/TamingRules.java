package com.hexvane.cozytalesranching.taming;

import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The numbers of befriending and friendship, kept free of engine types so they can be unit tested. */
public final class TamingRules {
    public static final int MAX_HEARTS = 5;
    /** Friendship from the first pet of an in-game day, in hearts. */
    public static final float PET_FRIENDSHIP = 0.25f;
    public static final float FEED_BASIC_FRIENDSHIP = 0.1f;
    public static final float FEED_PREFERRED_FRIENDSHIP = 0.15f;
    public static final float FEED_FAVORITE_FRIENDSHIP = 0.3f;
    /** Real time an owned animal needs between two friendship-giving meals. */
    public static final long FEED_COOLDOWN_MS = 20_000L;
    /** A wild animal forgets half-finished befriending after this long without food. */
    public static final long TAME_PROGRESS_TIMEOUT_MS = 5 * 60_000L;
    private static final long GAME_DAY_MS = 86_400_000L;

    public enum FoodKind { NONE, BASIC, PREFERRED, FAVORITE }

    public enum Gate { OK, BLOCKED_WORLD, NO_PERMISSION, AT_LIMIT }

    public record Friendship(int hearts, float progress) {}

    private TamingRules() {}

    /** The best list the item is on; a treat that is also a basic food counts as the treat. */
    @Nonnull
    public static FoodKind foodKind(@Nonnull SpeciesDefinition species, @Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) return FoodKind.NONE;
        if (species.FavoriteTreats.contains(itemId)) return FoodKind.FAVORITE;
        if (species.PreferredFoods.contains(itemId)) return FoodKind.PREFERRED;
        if (species.BasicFoods.contains(itemId)) return FoodKind.BASIC;
        return FoodKind.NONE;
    }

    /** How many feedings one item counts for while befriending: favourites count double. */
    public static int tamePoints(@Nonnull FoodKind kind) {
        return switch (kind) {
            case NONE -> 0;
            case BASIC, PREFERRED -> 1;
            case FAVORITE -> 2;
        };
    }

    public static int feedingsToTame(@Nonnull SpeciesDefinition species) {
        return Math.max(1, species.FeedingsToTame);
    }

    public static float feedFriendship(@Nonnull FoodKind kind) {
        return switch (kind) {
            case NONE -> 0f;
            case BASIC -> FEED_BASIC_FRIENDSHIP;
            case PREFERRED -> FEED_PREFERRED_FRIENDSHIP;
            case FAVORITE -> FEED_FAVORITE_FRIENDSHIP;
        };
    }

    /** Adds friendship; it never goes down and stops at {@link #MAX_HEARTS} with no progress left over. */
    @Nonnull
    public static Friendship addFriendship(int hearts, float progress, float amount) {
        int h = Math.clamp(hearts, 0, MAX_HEARTS);
        if (h >= MAX_HEARTS) return new Friendship(MAX_HEARTS, 0f);
        float p = Math.max(0f, progress) + Math.max(0f, amount);
        while (p >= 1f && h < MAX_HEARTS) {
            p -= 1f;
            h++;
        }
        if (h >= MAX_HEARTS) p = 0f;
        return new Friendship(h, p);
    }

    /** In-game day number for a game-clock instant in epoch milliseconds. */
    public static long gameDay(long gameTimeMs) {
        return Math.floorDiv(gameTimeMs, GAME_DAY_MS);
    }

    /**
     * An animal can be petted once per in-game day (a day on the world's game clock, not 24 real hours). A second
     * pet the same day gives nothing: no friendship, hearts, sound or animation. {@code lastPettedGameMs} 0 means never.
     */
    public static boolean canPetForFriendship(long lastPettedGameMs, long nowGameMs) {
        return lastPettedGameMs == 0L || gameDay(nowGameMs) != gameDay(lastPettedGameMs);
    }

    public static boolean canFeedForFriendship(long lastFedMs, long nowMs) {
        return lastFedMs == 0L || nowMs - lastFedMs >= FEED_COOLDOWN_MS;
    }

    /**
     * New taming and claiming are refused in blocked worlds (unless the player may bypass them), without
     * the tame node, or at the per-player animal limit. Owned animals keep working everywhere.
     */
    @Nonnull
    public static Gate tameGate(boolean worldBlocked, boolean canBypassWorlds, boolean hasTamePermission, int ownedCount, int maxAnimals) {
        if (worldBlocked && !canBypassWorlds) return Gate.BLOCKED_WORLD;
        if (!hasTamePermission) return Gate.NO_PERMISSION;
        if (ownedCount >= maxAnimals) return Gate.AT_LIMIT;
        return Gate.OK;
    }

    /** Chance that a shy animal startles at a hurried approach. Crouching never startles it. */
    public static boolean startles(double shyness, boolean crouching, boolean hurried, double roll) {
        if (crouching || !hurried) return false;
        return roll < Math.clamp(shyness, 0.0, 1.0);
    }

    /** Use prompt on a wild animal the mod befriends: holding a food it eats, befriend; anything else, look closer. */
    @Nonnull
    public static String wildHintKey(@Nonnull FoodKind held) {
        return held == FoodKind.NONE ? "cozyranch_taming.hint.greet" : "cozyranch_taming.hint.befriend";
    }

    /**
     * Nameplate text for an owned animal: its name when plates are on, otherwise none. An unnamed animal has no
     * plate; every new friend is asked for a name when it is tamed or claimed.
     */
    @Nullable
    public static String nameplateText(@Nullable String name, boolean showNameplates) {
        if (!showNameplates || name == null || name.isBlank()) return null;
        return name;
    }

    /** Names are trimmed, stripped of control characters and kept short. Returns null when nothing is left. */
    @Nullable
    public static String cleanName(@Nullable String raw, int maxLength) {
        if (raw == null) return null;
        StringBuilder b = new StringBuilder(raw.length());
        raw.codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(b::appendCodePoint);
        String s = b.toString().strip().replaceAll("\\s+", " ");
        if (s.isEmpty()) return null;
        if (s.codePointCount(0, s.length()) > maxLength) s = s.substring(0, s.offsetByCodePoints(0, maxLength)).strip();
        return s;
    }
}
