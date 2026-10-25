package com.hexvane.cozytalesranching.animal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Copies and compares {@link AnimalRecord}s field by field over every saved (non-static, non-transient) field, so a
 * field added later is covered without touching this class. Used to tell a real change from a position-only one, and
 * to hand the saver a private copy it can serialise without the store's lock.
 */
final class RecordFields {
    /** The last seen spot: updated by the recovery sweep as animals wander, saved with the next real change. */
    static final Set<String> POSITION = Set.of("lastX", "lastY", "lastZ");

    /**
     * Happiness drifts toward its target every care pass (a fall from full to half moves ~0.025 a minute). A drift
     * smaller than this since the last save is a soft change, like a position: written with the next real save, within
     * {@link AnimalStore#SOFT_SAVE_MS}, when the entity unloads, and at shutdown.
     */
    static final float HAPPINESS_STEP = 0.1f;

    private static final Field[] ALL;
    private static final Field[] NON_POSITION;
    private static final Field[] POSITION_FIELDS;

    static {
        List<Field> all = new ArrayList<>();
        List<Field> rest = new ArrayList<>();
        List<Field> pos = new ArrayList<>();
        for (Field f : AnimalRecord.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isTransient(m) || f.isSynthetic()) continue;
            all.add(f);
            (POSITION.contains(f.getName()) ? pos : rest).add(f);
        }
        ALL = all.toArray(new Field[0]);
        NON_POSITION = rest.toArray(new Field[0]);
        POSITION_FIELDS = pos.toArray(new Field[0]);
    }

    private RecordFields() {}

    /** A copy with its own lists; enums, strings, UUIDs and boxed values are immutable and shared. */
    @Nonnull
    static AnimalRecord copy(@Nonnull AnimalRecord r) {
        AnimalRecord c = new AnimalRecord();
        try {
            for (Field f : ALL) {
                Object v = f.get(r);
                if (v instanceof List<?> list) v = new ArrayList<>(list);
                f.set(c, v);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("AnimalRecord fields must be public", e);
        }
        return c;
    }

    static boolean sameExceptPosition(@Nonnull AnimalRecord a, @Nonnull AnimalRecord b) {
        return same(NON_POSITION, a, b);
    }

    static boolean samePosition(@Nonnull AnimalRecord a, @Nonnull AnimalRecord b) {
        return same(POSITION_FIELDS, a, b);
    }

    private static boolean same(@Nonnull Field[] fields, @Nonnull AnimalRecord a, @Nonnull AnimalRecord b) {
        try {
            for (Field f : fields) {
                if (f.getName().equals("happiness")) {
                    if (Math.abs(a.happiness - b.happiness) >= HAPPINESS_STEP || Float.isNaN(a.happiness) != Float.isNaN(b.happiness)) return false;
                    continue;
                }
                if (!Objects.equals(f.get(a), f.get(b))) return false;
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("AnimalRecord fields must be public", e);
        }
        return true;
    }
}
