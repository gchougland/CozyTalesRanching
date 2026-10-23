package com.hexvane.cozytalesranching.care;

import com.hexvane.cozytalesranching.breeding.TraitBook;
import java.util.Collection;
import javax.annotation.Nonnull;

/**
 * What an animal's traits change in care, read from the effects in {@code Traits.json} so any trait carrying an
 * effect gets it (the bundled ones: {@code hardy}, {@code gourmet}, {@code efficient_eater}, {@code affectionate}).
 *
 * @param declineMultiplier how fast happiness falls (below 1 is slower); {@code HappinessDeclineMultiplier}
 * @param floor             happiness never drifts below this; {@code HappinessFloor}, else {@code DeclineTraitFloor} for a
 *                          trait that slows the decline, else {@link CareRules#FLOOR}
 * @param treatMultiplier   favourite treat friendship and its happiness part; {@code TreatBonusMultiplier}
 * @param foodUse           share of trough meals that use up an item; {@code FoodUseMultiplier}
 * @param friendship        friendship gained from care; {@code FriendshipGainMultiplier}
 */
public record CareTraits(double declineMultiplier, float floor, double treatMultiplier, double foodUse, double friendship) {
    public static final String HAPPINESS_DECLINE = "HappinessDeclineMultiplier";
    public static final String HAPPINESS_FLOOR = "HappinessFloor";
    public static final String TREAT_BONUS = "TreatBonusMultiplier";
    public static final String FOOD_USE = "FoodUseMultiplier";
    public static final String FRIENDSHIP_GAIN = "FriendshipGainMultiplier";

    public static final CareTraits NONE = new CareTraits(1.0, CareRules.FLOOR, 1.0, 1.0, 1.0);

    @Nonnull
    public static CareTraits of(@Nonnull TraitBook book, @Nonnull Collection<String> traits, float declineTraitFloor) {
        if (traits.isEmpty()) return NONE;
        float floor = CareRules.FLOOR;
        for (String id : traits) {
            TraitBook.Trait t = book.get(id).orElse(null);
            if (t == null) continue;
            Double own = t.Effects.get(HAPPINESS_FLOOR);
            Double decline = t.Effects.get(HAPPINESS_DECLINE);
            if (own != null) floor = Math.max(floor, (float) (double) own);
            else if (decline != null && decline > 0 && decline < 1) floor = Math.max(floor, declineTraitFloor);
        }
        return new CareTraits(
            book.multiplier(traits, HAPPINESS_DECLINE),
            Math.clamp(floor, CareRules.FLOOR, 1f),
            book.multiplier(traits, TREAT_BONUS),
            Math.clamp(book.multiplier(traits, FOOD_USE), 0.0, 1.0),
            book.multiplier(traits, FRIENDSHIP_GAIN)
        );
    }
}
