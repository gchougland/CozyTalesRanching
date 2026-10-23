package com.hexvane.cozytalesranching.prefs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One player's ranch settings, as saved in {@code Prefs/<uuid>.json}. Everything is on until the player turns it off. */
public final class PlayerPrefs {
    /** The player's UUID, for people reading the file. The file name is what counts. */
    @Nullable public String Player;
    /** Ranch chat lines (feedback and flavour). Messages a player must see to act are always sent. */
    public boolean Chat = true;
    /** Ranch sound effects. */
    public boolean Sounds = true;
    public long SavedAtMs;

    public boolean get(@Nonnull Setting setting) {
        return switch (setting) {
            case CHAT -> Chat;
            case SOUNDS -> Sounds;
        };
    }

    public void set(@Nonnull Setting setting, boolean on) {
        switch (setting) {
            case CHAT -> Chat = on;
            case SOUNDS -> Sounds = on;
        }
    }

    @Nonnull
    public PlayerPrefs copy() {
        PlayerPrefs p = new PlayerPrefs();
        p.Player = Player;
        p.Chat = Chat;
        p.Sounds = Sounds;
        p.SavedAtMs = SavedAtMs;
        return p;
    }

    /** The two toggles in the journal's settings page. */
    public enum Setting {
        CHAT("chat"),
        SOUNDS("sounds");

        public final String id;

        Setting(@Nonnull String id) {
            this.id = id;
        }

        @Nullable
        public static Setting byId(@Nullable String id) {
            for (Setting s : values()) if (s.id.equals(id)) return s;
            return null;
        }
    }
}
