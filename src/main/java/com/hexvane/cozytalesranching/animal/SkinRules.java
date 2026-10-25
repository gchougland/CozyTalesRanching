package com.hexvane.cozytalesranching.animal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The pure part of keeping an animal's skin (its model's random attachments) through taming, role swaps, reloads and
 * breeding. No engine types, so it is unit tested; {@link AnimalSkins} feeds it from model assets.
 * <p>
 * How skins work in Hytale: a model asset may declare {@code RandomAttachmentSets} (set id to attachment id to an
 * attachment: a model, a texture, a gradient). A new entity rolls one attachment id per set; the chosen ids live on the
 * entity's model ({@code Model.randomAttachmentIds}), are saved with it ({@code PersistentModel}) and resolve against
 * the entity's own model asset when it loads. Skin packs (Celly's Wildlife Skins) override vanilla wild model assets
 * with such sets. Our ranch model assets do not have those sets, so we keep the ids on the record and draw the
 * attachments from the wild model asset they came from, when both share the same base model (so every node the
 * attachments hang on exists).
 */
public final class SkinRules {
    private SkinRules() {}

    /** One attachment: what the client draws. A piece with no model or no texture is an empty choice. */
    public record Piece(@Nullable String model, @Nullable String texture, @Nullable String gradientSet, @Nullable String gradientId) {
        boolean drawable() {
            return model != null && !model.isEmpty() && texture != null && !texture.isEmpty();
        }
    }

    /** What a model asset says about attachments. */
    public record AssetView(@Nonnull String id, @Nullable String base, @Nonnull List<Piece> defaults, @Nonnull Map<String, Map<String, Piece>> sets) {
        public boolean hasSets() {
            return !sets.isEmpty();
        }
    }

    /** A skin as stored on a record: the model asset the attachment ids belong to, and the ids by set. */
    public record Skin(@Nonnull String source, @Nonnull Map<String, String> ids) {}

    /** The attachments a model should draw and the ids it should carry. */
    public record Result(@Nonnull Map<String, String> ids, @Nonnull List<Piece> pieces) {}

    /** Two models can share attachments when they are drawn from the same base model file. */
    public static boolean sameBase(@Nullable String a, @Nullable String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        return normalise(a).equals(normalise(b));
    }

    @Nonnull
    private static String normalise(@Nonnull String path) {
        return path.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    /**
     * The ids the animal's model should carry.
     * <ul>
     *   <li>{@code fresh} (the entity was just made or changed model asset: tamed, grown up, out of its house, released):
     *   the engine has just rolled the asset's own sets, so the record wins for every set it has; sets the record does
     *   not have keep the roll.</li>
     *   <li>otherwise (loaded, or the model changed in place: shearing, a role action): the live model wins for sets
     *   its own asset defines (so a sheared sheep stays sheared), and the record supplies the sets the asset does not
     *   know (the skin the engine dropped when it rebuilt the model).</li>
     * </ul>
     */
    @Nonnull
    public static Map<String, String> mergeIds(@Nullable Map<String, String> live, @Nullable Map<String, String> record,
                                               @Nonnull Set<String> nativeSets, boolean fresh) {
        Map<String, String> out = new LinkedHashMap<>();
        if (fresh) {
            if (live != null) putValid(out, live);
            if (record != null) putValid(out, record);
            return out;
        }
        if (live != null) {
            for (Map.Entry<String, String> e : live.entrySet()) {
                if (nativeSets.contains(e.getKey()) && valid(e)) out.put(e.getKey(), e.getValue());
            }
        }
        if (record != null) {
            for (Map.Entry<String, String> e : record.entrySet()) {
                if (!nativeSets.contains(e.getKey()) && valid(e)) out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static void putValid(@Nonnull Map<String, String> out, @Nonnull Map<String, String> in) {
        for (Map.Entry<String, String> e : in.entrySet()) {
            if (valid(e)) out.put(e.getKey(), e.getValue());
        }
    }

    private static boolean valid(@Nonnull Map.Entry<String, String> e) {
        return e.getKey() != null && !e.getKey().isEmpty() && e.getValue() != null && !e.getValue().isEmpty();
    }

    /**
     * What the animal draws with {@code ids} on its model asset {@code target}, given the wild model asset the skin came
     * from ({@code source}, may be null or the same asset).
     * <ul>
     *   <li>An id {@code target} defines resolves on {@code target}, as the engine would.</li>
     *   <li>Any other id resolves on {@code source}, only when both share a base model and {@code source} still has
     *   sets (a skin pack removed later leaves the plain model).</li>
     *   <li>When at least one attachment comes from {@code source}, the default attachments are {@code source}'s, so
     *   the animal looks as it did in the wild (a skin pack that replaces a default flower does not get a second one);
     *   otherwise {@code target}'s.</li>
     * </ul>
     * With no source pieces the result is exactly what the engine draws for {@code target} with these ids.
     */
    @Nonnull
    public static Result resolve(@Nonnull AssetView target, @Nullable AssetView source, @Nonnull Map<String, String> ids) {
        boolean sourceUsable = source != null && source.hasSets() && !source.id().equals(target.id()) && sameBase(source.base(), target.base());
        List<Piece> own = new ArrayList<>();
        List<Piece> borrowed = new ArrayList<>();
        Map<String, String> kept = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : ids.entrySet()) {
            Map<String, Piece> set = target.sets().get(e.getKey());
            if (set != null && set.containsKey(e.getValue())) {
                Piece p = set.get(e.getValue());
                if (p != null && p.drawable()) own.add(p);
                kept.put(e.getKey(), e.getValue());
                continue;
            }
            if (sourceUsable) {
                Map<String, Piece> sset = source.sets().get(e.getKey());
                Piece p = sset == null ? null : sset.get(e.getValue());
                if (p != null && p.drawable()) borrowed.add(p);
            }
            // a set nobody here knows is kept on the ids, so it is not lost while the skin cannot be drawn
            kept.put(e.getKey(), e.getValue());
        }
        List<Piece> pieces = new ArrayList<>(borrowed.isEmpty() ? target.defaults() : source.defaults());
        pieces.addAll(own);
        pieces.addAll(borrowed);
        return new Result(kept, dedupe(pieces));
    }

    @Nonnull
    private static List<Piece> dedupe(@Nonnull List<Piece> pieces) {
        return new ArrayList<>(new LinkedHashSet<>(pieces));
    }

    /** True when two attachment lists draw the same thing (order kept, as the client draws in order). */
    public static boolean samePieces(@Nullable List<Piece> a, @Nullable List<Piece> b) {
        List<Piece> x = a == null ? List.of() : a;
        List<Piece> y = b == null ? List.of() : b;
        return x.equals(y);
    }

    /** True when two id maps hold the same entries (null and empty are the same). */
    public static boolean sameIds(@Nullable Map<String, String> a, @Nullable Map<String, String> b) {
        Map<String, String> x = a == null ? Map.of() : a;
        Map<String, String> y = b == null ? Map.of() : b;
        return x.equals(y);
    }

    /**
     * A baby's skin from its parents. Parents count only when they have a skin and the same adult look as the baby
     * (a stag fawn takes after a stag, so a doe's head flowers never end up on a stag). Two such parents with the same
     * source mix set by set; otherwise one is picked whole. Null when no parent fits: the caller then rolls one from the
     * wild model of the baby's look, or leaves the baby plain.
     */
    @Nullable
    public static Skin inherit(@Nullable Skin a, @Nullable String roleA, @Nullable Skin b, @Nullable String roleB,
                               @Nullable String babyAdultRole, @Nonnull RandomGenerator rng) {
        List<Skin> fit = new ArrayList<>(2);
        if (fits(a, roleA, babyAdultRole)) fit.add(a);
        if (fits(b, roleB, babyAdultRole)) fit.add(b);
        if (fit.isEmpty()) return null;
        if (fit.size() == 1) return copy(fit.get(0));
        Skin x = fit.get(0);
        Skin y = fit.get(1);
        if (!x.source().equals(y.source())) return copy(fit.get(rng.nextInt(2)));
        Map<String, String> ids = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>(x.ids().keySet());
        keys.addAll(y.ids().keySet());
        for (String k : keys) {
            String vx = x.ids().get(k);
            String vy = y.ids().get(k);
            String v = vx == null ? vy : vy == null ? vx : (rng.nextBoolean() ? vx : vy);
            if (v != null && !v.isEmpty()) ids.put(k, v);
        }
        return new Skin(x.source(), ids);
    }

    private static boolean fits(@Nullable Skin s, @Nullable String role, @Nullable String babyAdultRole) {
        if (s == null || s.ids().isEmpty() || s.source().isEmpty()) return false;
        return babyAdultRole == null || babyAdultRole.isEmpty() || role == null || Objects.equals(role, babyAdultRole);
    }

    @Nonnull
    private static Skin copy(@Nonnull Skin s) {
        return new Skin(s.source(), new LinkedHashMap<>(s.ids()));
    }

    /** The record's skin, or null when it has none. */
    @Nullable
    public static Skin of(@Nullable String source, @Nullable Map<String, String> ids) {
        if (source == null || source.isEmpty() || ids == null || ids.isEmpty()) return null;
        return new Skin(source, ids);
    }
}
