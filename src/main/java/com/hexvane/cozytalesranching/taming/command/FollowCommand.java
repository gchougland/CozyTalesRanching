package com.hexvane.cozytalesranching.taming.command;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.taming.TamingApi;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * {@code /ranch follow} and {@code /ranch stay}: the looked-at animal, or with nothing looked at, every
 * animal of yours nearby ({@code follow}) or every follower in this world ({@code stay}).
 */
public final class FollowCommand extends AbstractPlayerCommand {
    private static final double NEARBY = 16.0;
    private final boolean follow;

    private FollowCommand(@Nonnull String name, @Nonnull String descKey, boolean follow) {
        super(name, descKey);
        this.follow = follow;
        this.setPermissionGroups("hytale:Adventurer");
        this.requirePermission(RanchPermissions.FOLLOW);
    }

    @Nonnull
    public static FollowCommand follow() {
        return new FollowCommand("follow", "cozyranch_taming.commands.follow.desc", true);
    }

    @Nonnull
    public static FollowCommand stay() {
        return new FollowCommand("stay", "cozyranch_taming.commands.stay.desc", false);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Optional<TamingCommands.Target> looked = TamingCommands.lookedAt(ref, store);
        if (looked.isPresent()) {
            if (!TamingCommands.mayManage(playerRef, looked.get().record())) {
                playerRef.sendMessage(Message.translation("cozyranch_taming.command.not_yours"));
                return;
            }
            TamingApi.setFollowing(looked.get().record(), follow, looked.get().entity(), playerRef, store);
            return;
        }
        TransformComponent me = store.getComponent(ref, TransformComponent.getComponentType());
        int changed = 0;
        for (AnimalRecord record : Ranch.animals().ownedBy(playerRef.getUuid())) {
            if (record.following == follow) continue;
            Ref<EntityStore> animal = TamingCommands.entityOf(record, store);
            if (animal == null) continue;
            if (follow) {
                TransformComponent t = store.getComponent(animal, TransformComponent.getComponentType());
                if (me == null || t == null || t.getPosition().distance(me.getPosition()) > NEARBY) continue;
            }
            TamingApi.setFollowing(record, follow, animal, playerRef, store);
            changed++;
        }
        if (changed == 0) playerRef.sendMessage(Message.translation(follow ? "cozyranch_taming.follow.none_nearby" : "cozyranch_taming.follow.none_following"));
    }
}
