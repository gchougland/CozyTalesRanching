package com.hexvane.cozytalesranching.journal;

import com.hexvane.cozytalesranching.CozyTalesRanchingPlugin;
import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Owned by the journal role: the Rancher's Journal item, pages, discoveries and memories. */
public final class JournalFeature {
    /** The {@code OpenCustomUI} page id the journal item names. */
    public static final String PAGE_ID = "CozyRanchJournal";
    /** With the other roles' flushes: after the worlds shut down. */
    private static final short SAVE_ON_SHUTDOWN = ShutdownEvent.SHUTDOWN_WORLDS + 4;

    @Nullable private static volatile Path dataDirectory;

    private JournalFeature() {}

    /** Called once from the plugin's setup(). */
    public static void register(@Nonnull CozyTalesRanchingPlugin plugin) {
        dataDirectory = plugin.getDataDirectory();
        JournalBook.install(JournalBook.load(dataDirectory));
        JournalService journal = new JournalService(dataDirectory);
        journal.load();
        JournalService.install(journal);
        JournalListeners.register(plugin);
        OpenCustomUIInteraction.registerCustomPageSupplier(plugin, JournalPage.class, PAGE_ID, (ref, accessor, playerRef, context) -> {
            if (!RanchPermissions.has(playerRef.getUuid(), RanchPermissions.JOURNAL)) {
                playerRef.sendMessage(ServerUiOut.message(Txt.j("open.denied")));
                return null;
            }
            JournalPage page = JournalPage.live(playerRef, null);
            if (page != null) JournalPage.playOpenSound(playerRef);
            return page;
        });
        Ranch.installJournal(JournalPage::open);
        plugin.getEventRegistry().register(SAVE_ON_SHUTDOWN, ShutdownEvent.class, event -> journal.close());
        Runtime.getRuntime().addShutdownHook(new Thread(journal::close, "CozyRanch-JournalShutdown"));
        plugin.getLogger().atInfo().log("Journal registered: page %s, %d species pages", PAGE_ID, JournalBook.get().Species.size());
    }

    /** Re-reads Journal.json (bundled, or the server's override in the plugin data folder). */
    public static void reload() {
        JournalBook.install(JournalBook.load(dataDirectory));
    }
}
