package com.hexvane.cozytalesranching.animal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The crate's pure rules: no engine calls, so they are unit tested. */
final class CrateRules {
    private CrateRules() {}

    /** May this player put this animal in a crate? Its owner, an admin who manages any, or anyone for an unowned one. */
    static boolean mayCapture(@Nonnull UUID player, @Nullable UUID owner, boolean manageAny) {
        return owner == null || owner.equals(player) || manageAny;
    }

    /** Whether a crate still holds this record: it is in a crate, and in this crate rather than an older copy of it. */
    static boolean holds(@Nonnull AnimalRecord.State state, long recordGeneration, long crateGeneration) {
        return state == AnimalRecord.State.CRATED && recordGeneration == crateGeneration;
    }

    /**
     * The crate's stored role key. The client's item tooltip shows {@code Capture Crate ({npc})}, where {@code npc} is the
     * translation of {@code server.npcRoles.<key>.name}, so a key of its own gives each crate its own line of text. Read from
     * the client (0.7.5): tooltip builder at 1403b3390, {@code server.items.Tool_Capture_Crate.nameFull}. With the translation
     * missing the client shows the plain item name.
     */
    @Nonnull
    static String roleKey(@Nonnull UUID animalId) {
        return "cozyranch_" + animalId.toString().replace("-", "");
    }

    @Nonnull
    static String translationKey(@Nonnull UUID animalId) {
        return "server.npcRoles." + roleKey(animalId) + ".name";
    }

    /** Fills {@code {name}} parameters in a line of a lang file. */
    @Nonnull
    static String fill(@Nonnull String template, @Nonnull Map<String, String> params) {
        String out = template;
        for (Map.Entry<String, String> e : params.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue());
        return out;
    }

    /** The traits after the rest of the line: {@code ", Lucky, Hardy"}, or nothing. */
    @Nonnull
    static String traitList(@Nonnull List<String> traitNames) {
        StringBuilder out = new StringBuilder();
        for (String t : traitNames) out.append(", ").append(t);
        return out.toString();
    }
}
