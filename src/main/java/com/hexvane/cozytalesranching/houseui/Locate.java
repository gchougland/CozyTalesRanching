package com.hexvane.cozytalesranching.houseui;

import com.hexvane.cozytalesranching.animal.AnimalRecord;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The chat line of the Locate button: where a resident is and how far, worked out without server types. */
public final class Locate {
    /** Closer than this reads "right beside you". */
    public static final double NEAR = 4.0;
    private static final String[] COMPASS = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};

    private Locate() {}

    /** A position; {@code live} when read from the animal's entity now, false for its last saved position. */
    public record Fix(double x, double y, double z, boolean live) {}

    @Nonnull
    public static Txt describe(@Nonnull Txt name, boolean inside, @Nonnull AnimalRecord.State state, boolean sameWorld,
                               @Nullable Fix animal, @Nullable Fix player) {
        if (inside || state == AnimalRecord.State.STORED) return Txt.ui("locate.inside").with("name", name);
        if (state == AnimalRecord.State.MISSING) return Txt.ui("locate.lost").with("name", name);
        if (!sameWorld) return Txt.ui("locate.elsewhere").with("name", name);
        if (animal == null || player == null) return Txt.ui("locate.unknown").with("name", name);
        double dx = animal.x() - player.x(), dz = animal.z() - player.z();
        double distance = Math.sqrt(dx * dx + dz * dz + (animal.y() - player.y()) * (animal.y() - player.y()));
        if (distance < NEAR) return Txt.ui(animal.live() ? "locate.near" : "locate.last.near").with("name", name);
        return Txt.ui(animal.live() ? "locate.far" : "locate.last")
            .with("name", name)
            .with("distance", (int) Math.round(distance))
            .with("direction", Txt.ui("dir." + compass(dx, dz)));
    }

    /** One of eight compass words for a step of (dx, dz): north is -Z, east is +X. */
    @Nonnull
    static String compass(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.floorMod(Math.round(degrees / 45.0), 8L);
        return COMPASS[sector];
    }
}
