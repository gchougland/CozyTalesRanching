package com.hexvane.cozytalesranching.items;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hexvane.cozytalesranching.items.GrowthRules.Verdict;
import org.junit.jupiter.api.Test;

class GrowthRulesTest {
    @Test
    void aPlantedCropWithStagesLeftGrows() {
        assertEquals(Verdict.GROW, GrowthRules.judge(5, true, 0f));
        assertEquals(Verdict.GROW, GrowthRules.judge(5, true, 2.7f));
        assertEquals(Verdict.GROW, GrowthRules.judge(5, true, 3.99f));
        assertEquals(Verdict.GROW, GrowthRules.judge(2, true, 0.5f));
    }

    @Test
    void theLastStageIsFullyGrown() {
        assertEquals(Verdict.FULLY_GROWN, GrowthRules.judge(5, true, 4f));
        assertEquals(Verdict.FULLY_GROWN, GrowthRules.judge(5, true, 4.5f));
        // vanilla can leave progress at the stage count just before it removes the growth state
        assertEquals(Verdict.FULLY_GROWN, GrowthRules.judge(5, true, 5f));
    }

    @Test
    void blocksWithoutStagesAreNotCrops() {
        assertEquals(Verdict.NOT_A_CROP, GrowthRules.judge(0, false, 0f));
        assertEquals(Verdict.NOT_A_CROP, GrowthRules.judge(0, true, 1f));
        assertEquals(Verdict.NOT_A_CROP, GrowthRules.judge(1, true, 0f));
    }

    @Test
    void aFarmingBlockNobodyPlantedIsLeftAlone() {
        // vanilla's ChangeFarmingStage would make a growth state at the last stage; a tuft must not be spent on it
        assertEquals(Verdict.NOT_GROWING, GrowthRules.judge(5, false, 0f));
        assertEquals(Verdict.NOT_GROWING, GrowthRules.judge(5, true, Float.NaN));
    }
}
