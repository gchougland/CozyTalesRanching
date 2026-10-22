package com.hexvane.cozytalesranching;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Permission nodes. Plain strings, so any provider (the built-in one, LuckPerms) can grant or deny them.
 * Player nodes default to the Adventurer group; admin nodes to no group (operators and explicit grants only).
 */
public final class RanchPermissions {
    public static final String TAME = "cozyranch.tame";
    public static final String FOLLOW = "cozyranch.follow";
    public static final String NAME = "cozyranch.name";
    public static final String HOUSE_PLACE = "cozyranch.house.place";
    public static final String HOUSE_MANAGE = "cozyranch.house.manage";
    public static final String TROUGH = "cozyranch.trough";
    public static final String BREED = "cozyranch.breed";
    public static final String BUTCHER = "cozyranch.butcher";
    public static final String RELEASE = "cozyranch.release";
    public static final String TRANSFER = "cozyranch.transfer";
    public static final String JOURNAL = "cozyranch.journal";

    public static final String ADMIN = "cozyranch.admin";
    public static final String ADMIN_RELOAD = "cozyranch.admin.reload";
    public static final String ADMIN_INSPECT = "cozyranch.admin.inspect";
    public static final String ADMIN_BYPASS_BLOCKED_WORLDS = "cozyranch.admin.bypassworlds";
    public static final String ADMIN_MANAGE_ANY = "cozyranch.admin.manageany";

    private static final String GROUP_PLAYERS = HytalePermissionsProvider.GROUP_ADVENTURER;

    private RanchPermissions() {}

    public static void register() {
        for (String node : new String[] {TAME, FOLLOW, NAME, HOUSE_PLACE, HOUSE_MANAGE, TROUGH, BREED, BUTCHER, RELEASE, TRANSFER, JOURNAL}) {
            PermissionsModule.registerPermission(node, GROUP_PLAYERS);
        }
        for (String node : new String[] {ADMIN, ADMIN_RELOAD, ADMIN_INSPECT, ADMIN_BYPASS_BLOCKED_WORLDS, ADMIN_MANAGE_ANY}) {
            PermissionsModule.registerPermission(node);
        }
    }

    public static boolean has(@Nonnull UUID player, @Nonnull String node) {
        PermissionsModule module = PermissionsModule.get();
        return module == null || module.hasPermission(player, node);
    }
}
