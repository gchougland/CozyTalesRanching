package com.hexvane.cozytalesranching.api.event;

import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.HytaleServer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/** Fires ranch events on the server event bus. A listener that throws is logged and never breaks the ranch. */
public final class RanchEvents {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private RanchEvents() {}

    @Nonnull
    @SuppressWarnings("unchecked")
    public static <E extends IEvent<Void>> E fire(@Nonnull E event) {
        try {
            HytaleServer.get().getEventBus().dispatchFor((Class<E>) event.getClass()).dispatch(event);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "A listener failed on " + event.getClass().getSimpleName(), e);
        }
        return event;
    }
}
