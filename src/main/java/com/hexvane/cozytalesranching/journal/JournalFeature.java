package com.hexvane.cozytalesranching.journal;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import javax.annotation.Nonnull;

/** Owned by the journal role: the Rancher's Journal, discoveries and memories. */
public final class JournalFeature {
    private JournalFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {}

    /** Called from /ranch reload. */
    public static void reload() {}
}