package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.care.HabitatConfig.Feature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One habitat scan around a house, done a few columns at a time so it can be spread over ticks. A column is
 * every cell from {@code ScanAbove} above the house's origin to {@code ScanBelow} below it; the columns form a
 * disc of the scan radius. A column with any cell not in memory is skipped whole (nothing is loaded).
 * <p>
 * Shade and open sky are judged per column. A surface is a solid, fluid or leaves cell with at least two passable
 * cells above it; the column's ground is the surface nearest the house's height, within {@code GroundTolerance}
 * (so a roof three blocks up is not mistaken for ground). The column is <b>shaded</b> if anything solid, fluid or
 * leafy sits above its ground inside the scanned height, and <b>open</b> if nothing does. A column with no ground
 * near the house's height counts as neither.
 */
public final class HabitatScan {
    /** How a scan ended up for one house and species. */
    public record Result(boolean requirementsMet, float score, @Nonnull List<String> missing) {}

    private final HabitatConfig config;
    private final int cx;
    private final int cy;
    private final int cz;
    private final int[] colDx;
    private final int[] colDz;
    private int next;

    private final List<String> plainIds = new ArrayList<>();
    private final List<Feature> plain = new ArrayList<>();
    private final Map<String, Long> maskCache = new HashMap<>();
    private final int[] counts;
    private int columnsScanned;
    private int columnsSkipped;
    private int shadeColumns;
    private int openColumns;

    public HabitatScan(@Nonnull HabitatConfig config, int cx, int cy, int cz, int radius) {
        this.config = config;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        int r = Math.max(0, radius);
        List<int[]> cols = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r) cols.add(new int[] {dx, dz});
            }
        }
        colDx = new int[cols.size()];
        colDz = new int[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            colDx[i] = cols.get(i)[0];
            colDz[i] = cols.get(i)[1];
        }
        config.Features.forEach((id, f) -> {
            if (!f.isSpecial() && plain.size() < 64) {
                plainIds.add(id);
                plain.add(f);
            }
        });
        counts = new int[plain.size()];
    }

    public boolean done() {
        return next >= colDx.length;
    }

    public int totalColumns() {
        return colDx.length;
    }

    public int columnsScanned() {
        return columnsScanned;
    }

    public int columnsSkipped() {
        return columnsSkipped;
    }

    public int shadeColumns() {
        return shadeColumns;
    }

    public int openColumns() {
        return openColumns;
    }

    /** Scans up to {@code maxColumns} more columns. Returns how many cells were read. */
    public int step(@Nonnull HabitatGrid grid, int maxColumns) {
        int cells = 0;
        int top = cy + config.ScanAbove;
        int bottom = cy - config.ScanBelow;
        int[] col = new int[counts.length];
        for (int n = 0; n < maxColumns && !done(); n++, next++) {
            int x = cx + colDx[next];
            int z = cz + colDz[next];
            Arrays.fill(col, 0);
            boolean skipped = false;
            boolean aboveEmpty = true;
            int airRun = 0;
            boolean cover = false;
            int groundDistance = Integer.MAX_VALUE;
            boolean shaded = false;
            boolean open = false;
            for (int y = top; y >= bottom; y--) {
                cells++;
                if (!grid.loaded(x, y, z)) {
                    skipped = true;
                    break;
                }
                String block = grid.block(x, y, z);
                String fluid = grid.fluid(x, y, z);
                boolean solid = block != null && grid.solid(x, y, z);
                if (block != null) {
                    long mask = mask(block);
                    for (int i = 0; mask != 0 && i < plain.size(); i++, mask >>>= 1) {
                        if ((mask & 1L) != 0 && (!plain.get(i).Surface || aboveEmpty)) col[i]++;
                    }
                }
                if (fluid != null) {
                    for (int i = 0; i < plain.size(); i++) if (plain.get(i).matchesFluid(fluid)) col[i]++;
                }
                boolean leaves = block != null && config.isCover(block);
                if (!solid && fluid == null && !leaves) {
                    airRun++;
                } else {
                    int d = Math.abs(y - cy);
                    if (airRun >= 2 && d <= config.GroundTolerance && d < groundDistance) {
                        groundDistance = d;
                        shaded = cover;
                        open = !cover;
                    }
                    cover = true;
                    airRun = 0;
                }
                aboveEmpty = block == null && fluid == null;
            }
            if (skipped) {
                columnsSkipped++;
                continue;
            }
            columnsScanned++;
            for (int i = 0; i < col.length; i++) counts[i] += col[i];
            if (shaded) shadeColumns++;
            if (open) openColumns++;
        }
        return cells;
    }

    /** Cells (or columns for Shade and OpenSky) found for a feature id; -1 if the config does not know it. */
    public int count(@Nonnull String featureId) {
        Feature f = config.feature(featureId);
        if (f == null) return -1;
        return switch (f.Special) {
            case HabitatConfig.SPECIAL_SHADE -> shadeColumns;
            case HabitatConfig.SPECIAL_OPEN_SKY -> openColumns;
            case "" -> {
                int i = plainIds.indexOf(featureId);
                yield i < 0 ? 0 : counts[i];
            }
            default -> 0;
        };
    }

    /** Whether a feature is present. Unknown ids answer null; {@code troughFood} answers {@code TroughFood}. */
    @Nullable
    public Boolean present(@Nonnull String featureId, boolean troughFood) {
        Feature f = config.feature(featureId);
        if (f == null) return null;
        return switch (f.Special) {
            case HabitatConfig.SPECIAL_TROUGH_FOOD -> troughFood;
            case HabitatConfig.SPECIAL_OPEN_SKY -> columnsScanned > 0
                && openColumns >= Math.max(f.MinCount, (int) Math.ceil(f.MinFraction * columnsScanned));
            case HabitatConfig.SPECIAL_SHADE, "" -> count(featureId) >= f.MinCount;
            default -> null;
        };
    }

    /**
     * Requirements met when every known required feature is present. Score: half for the share of required
     * features present, half for the share of preferred ones (an empty list counts as all present).
     * Feature ids the config does not know are left out of both and never reported missing.
     */
    @Nonnull
    public Result evaluate(@Nonnull List<String> requires, @Nonnull List<String> prefers, boolean troughFood) {
        List<String> missing = new ArrayList<>();
        int[] req = tally(requires, troughFood, missing);
        int[] pref = tally(prefers, troughFood, missing);
        float reqShare = req[1] == 0 ? 1f : (float) req[0] / req[1];
        float prefShare = pref[1] == 0 ? 1f : (float) pref[0] / pref[1];
        return new Result(req[0] == req[1], 0.5f * reqShare + 0.5f * prefShare, missing);
    }

    private int[] tally(@Nonnull List<String> ids, boolean troughFood, @Nonnull List<String> missing) {
        int have = 0;
        int known = 0;
        for (String id : ids) {
            Boolean p = present(id, troughFood);
            if (p == null) continue;
            known++;
            if (p) have++;
            else if (!missing.contains(id)) missing.add(id);
        }
        return new int[] {have, known};
    }

    private long mask(@Nonnull String block) {
        Long cached = maskCache.get(block);
        if (cached != null) return cached;
        long m = 0;
        for (int i = 0; i < plain.size(); i++) if (plain.get(i).matchesBlock(block)) m |= 1L << i;
        maskCache.put(block, m);
        return m;
    }
}
