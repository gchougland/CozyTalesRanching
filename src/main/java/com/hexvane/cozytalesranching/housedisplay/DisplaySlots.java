package com.hexvane.cozytalesranching.housedisplay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Which resident sits on which point. Pure, so it is unit tested. */
final class DisplaySlots {
    private DisplaySlots() {}

    /**
     * The point index to animal id a house should show. Animals already on a point stay there while they are
     * still wanted (no shuffling when one more goes in); the first {@code points} of {@code inside} are wanted,
     * in the order given (the caller sorts them so the choice is stable); free points fill lowest first.
     *
     * @param current what is shown now (point index to animal id); indices at or past {@code points} are dropped
     * @param inside  residents inside the house, in a stable order
     * @param points  how many points the house has
     */
    @Nonnull
    static Map<Integer, UUID> plan(@Nonnull Map<Integer, UUID> current, @Nonnull List<UUID> inside, int points) {
        Map<Integer, UUID> out = new TreeMap<>();
        if (points <= 0 || inside.isEmpty()) return out;
        Set<UUID> wanted = new HashSet<>(inside.subList(0, Math.min(points, inside.size())));
        Set<UUID> placed = new HashSet<>();
        for (Map.Entry<Integer, UUID> e : current.entrySet()) {
            int i = e.getKey();
            UUID id = e.getValue();
            if (i >= 0 && i < points && id != null && wanted.contains(id) && placed.add(id)) out.put(i, id);
        }
        List<UUID> waiting = new ArrayList<>();
        for (UUID id : inside) {
            if (wanted.contains(id) && !placed.contains(id)) waiting.add(id);
        }
        int next = 0;
        for (UUID id : waiting) {
            while (next < points && out.containsKey(next)) next++;
            if (next >= points) break;
            out.put(next++, id);
        }
        return out;
    }
}
