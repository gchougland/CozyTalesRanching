package com.hexvane.cozytalesranching.behaviour;

/**
 * Whether a following animal is getting anywhere. Pure, for the taming follow pass.
 * <p>
 * The old check only counted an animal as stuck when it stood still. Inside a fenced pen (where housed animals live) a
 * follower whose owner is outside slides along the fence or circles looking for a way out: it keeps moving, so it was
 * never "stuck", and it only came over once the owner was past the teleport distance. Players saw it "keep wandering".
 * What counts now is progress toward where the owner is at the moment: how much closer the animal got this pass.
 */
public final class FollowProgress {
    /** Within this (blocks) the animal is with its owner (it stops 2 to 3 blocks off, plus slack); nothing to judge. */
    public static final double NEAR = 5.0;
    /** Coming closer by less than this per second does not count as progress. */
    public static final double MIN_APPROACH_PER_SECOND = 0.4;

    private FollowProgress() {}

    /**
     * Seconds without progress after this pass.
     *
     * @param beforeToOwner distance from where the animal was last pass to where the owner is now
     * @param nowToOwner    distance from where the animal is now to where the owner is now
     * @param seconds       time since last pass
     * @param stuckBefore   seconds without progress so far
     */
    public static double stuckSeconds(double beforeToOwner, double nowToOwner, double seconds, double stuckBefore) {
        if (nowToOwner <= NEAR) return 0;
        double approach = beforeToOwner - nowToOwner;
        return approach >= MIN_APPROACH_PER_SECOND * seconds ? 0 : stuckBefore + seconds;
    }
}
