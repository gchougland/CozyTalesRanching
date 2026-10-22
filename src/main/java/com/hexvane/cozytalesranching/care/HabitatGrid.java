package com.hexvane.cozytalesranching.care;

import javax.annotation.Nullable;

/** What a habitat scan reads. The world version reads only sections already in memory and loads nothing. */
public interface HabitatGrid {
    /** False for a cell whose section is not in memory; such a column is skipped. */
    boolean loaded(int x, int y, int z);

    /** Base block id (state suffix stripped), or null for an empty cell. */
    @Nullable String block(int x, int y, int z);

    /** The block's material is solid (ground, walls, leaves; not plants or air). */
    boolean solid(int x, int y, int z);

    /** Fluid id, or null for none. */
    @Nullable String fluid(int x, int y, int z);
}
