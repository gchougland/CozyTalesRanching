package com.hexvane.cozytalesranching.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RanchPrefsTest {
    static final UUID A = UUID.fromString("11111111-2222-4333-8444-555555555555");
    static final UUID B = UUID.fromString("66666666-7777-4888-9999-aaaaaaaaaaaa");

    @TempDir Path dir;

    @AfterEach
    void uninstall() {
        RanchPrefs.uninstall();
    }

    @Test
    void everythingIsOnByDefault() {
        assertTrue(RanchPrefs.chat(A), "not running: on");
        assertTrue(RanchPrefs.sounds(A));
        assertFalse(RanchPrefs.anySoundsOff());
        RanchPrefs.installForTest(new PrefsStore(dir));
        assertTrue(RanchPrefs.chat(A));
        assertTrue(RanchPrefs.sounds(A));
        assertTrue(RanchPrefs.of(A).Chat);
        assertTrue(RanchPrefs.of(A).Sounds);
        assertFalse(RanchPrefs.anySoundsOff());
        assertTrue(RanchChat.shows(A));
    }

    @Test
    void togglesAreSeparateAndPerPlayer() {
        RanchPrefs.installForTest(new PrefsStore(dir));
        assertFalse(RanchPrefs.toggle(A, PlayerPrefs.Setting.SOUNDS));
        assertFalse(RanchPrefs.sounds(A));
        assertTrue(RanchPrefs.chat(A), "sounds off leaves chat on");
        assertTrue(RanchPrefs.sounds(B), "another player keeps theirs");
        assertTrue(RanchPrefs.anySoundsOff());

        assertFalse(RanchPrefs.toggle(A, PlayerPrefs.Setting.CHAT));
        assertFalse(RanchChat.shows(A));
        assertTrue(RanchChat.shows(B));

        assertTrue(RanchPrefs.toggle(A, PlayerPrefs.Setting.SOUNDS));
        assertTrue(RanchPrefs.sounds(A));
        assertFalse(RanchPrefs.anySoundsOff());
        assertFalse(RanchPrefs.chat(A));
    }

    @Test
    void settingsSurviveARestart() {
        PrefsStore store = new PrefsStore(dir);
        RanchPrefs prefs = RanchPrefs.installForTest(store);
        RanchPrefs.set(A, PlayerPrefs.Setting.CHAT, false);
        RanchPrefs.set(B, PlayerPrefs.Setting.SOUNDS, false);
        prefs.close();
        assertTrue(Files.isRegularFile(dir.resolve(A + ".json")));

        PrefsStore again = new PrefsStore(dir);
        again.loadAll();
        assertFalse(again.chat(A));
        assertTrue(again.sounds(A));
        assertTrue(again.chat(B));
        assertFalse(again.sounds(B));
        assertTrue(again.anySoundsOff());
    }

    @Test
    void unchangedPlayersWriteNothing() {
        PrefsStore store = new PrefsStore(dir);
        assertFalse(store.set(A, PlayerPrefs.Setting.CHAT, true), "already on");
        assertFalse(store.hasDirty());
        assertEquals(0, store.saveDirty());
        assertFalse(Files.exists(dir.resolve(A + ".json")));
    }

    @Test
    void aBrokenFileFallsBackToThePreviousCopyThenToDefaults() throws Exception {
        PrefsStore store = new PrefsStore(dir);
        store.set(A, PlayerPrefs.Setting.SOUNDS, false);
        store.saveDirty();
        store.set(A, PlayerPrefs.Setting.CHAT, false);
        store.saveDirty();
        Files.writeString(dir.resolve(A + ".json"), "{ not json", StandardCharsets.UTF_8);
        PrefsStore again = new PrefsStore(dir);
        again.loadAll();
        assertFalse(again.sounds(A), "from .prev");
        assertTrue(again.chat(A), ".prev was written before chat was turned off");

        Files.writeString(dir.resolve(B + ".json"), "", StandardCharsets.UTF_8);
        PrefsStore third = new PrefsStore(dir);
        third.loadAll();
        assertTrue(third.chat(B));
        assertTrue(third.sounds(B));
    }
}
