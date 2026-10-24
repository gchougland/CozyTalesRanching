package com.hexvane.cozytalesranching.house;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.compat.claims.Claims;
import com.hexvane.cozytalesranching.house.HouseService.Result;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Who may do what with a house. Outside land claims (or without a claims mod, or with {@code ClaimsIntegration}
 * off) only the house's owner, with {@code cozyranch.house.manage}, or {@code cozyranch.admin.manageany}. Inside a
 * claim the claim's permissions stand in for house ownership: its owner may do everything, members and groups by
 * the claim's flag for the action ({@link Claims.Action}). Animals and their records always stay their owner's.
 * World thread.
 */
public final class HouseAccess {
    private HouseAccess() {}

    /** Rename, breeding, moving residents, calling back, locating: the claim's {@code INTERACT}. */
    @Nonnull
    public static Result manage(@Nonnull UUID actor, @Nonnull HouseRecord house) {
        return check(actor, house, Claims.Action.MANAGE);
    }

    /** Taking the house's produce: the claim's {@code CONTAINER}. */
    @Nonnull
    public static Result collect(@Nonnull UUID actor, @Nonnull HouseRecord house) {
        return check(actor, house, Claims.Action.COLLECT);
    }

    /** Breaking or picking up the house block: the claim's owner, or its {@code BREAK}. */
    public static boolean mayBreak(@Nonnull UUID actor, @Nonnull HouseRecord house) {
        boolean manageAny = RanchPermissions.has(actor, RanchPermissions.ADMIN_MANAGE_ANY);
        if (manageAny || actor.equals(house.ownerId)) return true;
        return HouseRules.mayBreak(actor, house.ownerId, false, claimAllows(actor, house, Claims.Action.BREAK));
    }

    @Nonnull
    private static Result check(@Nonnull UUID actor, @Nonnull HouseRecord house, @Nonnull Claims.Action action) {
        boolean manage = RanchPermissions.has(actor, RanchPermissions.HOUSE_MANAGE);
        boolean manageAny = RanchPermissions.has(actor, RanchPermissions.ADMIN_MANAGE_ANY);
        // the claim is only asked when the old rule would refuse
        boolean claim = !manageAny && !actor.equals(house.ownerId) && claimAllows(actor, house, action);
        return HouseRules.checkManage(actor, house.ownerId, manage, manageAny, claim);
    }

    private static boolean claimAllows(@Nonnull UUID actor, @Nonnull HouseRecord house, @Nonnull Claims.Action action) {
        return Claims.claimAllows(actor, house.worldName, house.x, house.y, house.z, action);
    }
}
