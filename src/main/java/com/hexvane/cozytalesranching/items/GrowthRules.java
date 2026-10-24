package com.hexvane.cozytalesranching.items;

import javax.annotation.Nonnull;

/**
 * When a Moss Tuft may push a crop on by one stage. Pure logic; {@link MossGrowthInteraction} feeds it what it reads
 * from the block. The stage change itself is vanilla's {@code ChangeFarmingStage}, which runs only when this says yes.
 */
public final class GrowthRules {
    public enum Verdict {
        /** Grow it one stage. */
        GROW,
        /** Not a farming block, or a farming block with fewer than two stages. */
        NOT_A_CROP,
        /** A farming block nobody planted (no growth state yet): vanilla would jump it to its last stage. */
        NOT_GROWING,
        /** Already at its last stage; a tuft would be wasted. */
        FULLY_GROWN
    }

    private GrowthRules() {}

    /**
     * @param stageCount  stages in the block's current stage set, or 0 when it has no farming data
     * @param hasGrowth   whether the block carries a growth state (a planted crop)
     * @param progress    the growth state's progress; its whole part is the stage index
     */
    @Nonnull
    public static Verdict judge(int stageCount, boolean hasGrowth, float progress) {
        if (stageCount < 2) return Verdict.NOT_A_CROP;
        if (!hasGrowth) return Verdict.NOT_GROWING;
        if (Float.isNaN(progress)) return Verdict.NOT_GROWING;
        int stage = (int) Math.floor(progress);
        if (stage >= stageCount - 1) return Verdict.FULLY_GROWN;
        return Verdict.GROW;
    }
}
