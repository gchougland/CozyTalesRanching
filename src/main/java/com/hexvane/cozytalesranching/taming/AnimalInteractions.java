package com.hexvane.cozytalesranching.taming;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.api.event.AnimalFriendshipChangedEvent;
import com.hexvane.cozytalesranching.api.event.AnimalOwnerChangedEvent;
import com.hexvane.cozytalesranching.api.event.AnimalTameAttemptEvent;
import com.hexvane.cozytalesranching.api.event.AnimalTamedEvent;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hexvane.cozytalesranching.compat.CompatGuard;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.prefs.RanchChat;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleMatch;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import java.util.Optional;
import java.util.UUID;
import com.hexvane.cozytalesranching.breeding.BreedingRules;
import com.hexvane.cozytalesranching.breeding.TraitRegistry;
import com.hexvane.cozytalesranching.care.CareApi;
import java.util.concurrent.ThreadLocalRandom;
import com.hexvane.cozytalesranching.predator.CalmRules;
import com.hexvane.cozytalesranching.predator.PredatorApi;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * What happens when a player uses or offers food to a supported animal. The first half runs inside the
 * interaction (reads only); every change is queued with {@link World#execute} so no store is written during a
 * tick and the held item is checked again before it is taken.
 */
public final class AnimalInteractions {
    /** Shared by every world thread, so access is synchronised inside. */
    private static final TameProgress PROGRESS = new TameProgress();

    private AnimalInteractions() {}

    /**
     * @return true when the mod handled the use and the engine's own reaction (vanilla taming or feeding) must
     *     not also run; false when the animal is not ours to handle or the engine reaction is harmless
     */
    public static boolean use(
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull Ref<EntityStore> animal,
        @Nullable ItemStack held,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        if (!animal.isValid() || !playerEntity.isValid()) return false;
        NPCEntity npc = accessor.getComponent(animal, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null) return false;
        String roleId = npc.getRoleName();
        Optional<RoleMatch> match = SpeciesRegistry.byRole(roleId);
        if (match.isEmpty() || !CompatGuard.isManageable(animal, accessor, roleId)) return false;
        SpeciesDefinition species = match.get().species();
        if (!RanchConfig.get().isSpeciesEnabled(species.Id)) return false;
        PlayerRef player = accessor.getComponent(playerEntity, PlayerRef.getComponentType());
        UUIDComponent animalUuid = accessor.getComponent(animal, UUIDComponent.getComponentType());
        if (player == null || animalUuid == null) return false;

        World world = accessor.getExternalData().getWorld();
        String itemId = ItemStack.isEmpty(held) ? null : held.getItemId();
        MovementStates moves = movement(playerEntity, accessor);
        boolean crouching = moves != null && moves.crouching;
        Use use = new Use(player, world, animalUuid.getUuid(), species, match.get().kind(), itemId, crouching, false);
        Optional<AnimalRecord> record = Ranch.animals().byEntity(use.animalUuid);
        RoleKind kind = use.kind;

        if (kind == RoleKind.WILD || kind == RoleKind.WILD_BABY) {
            if (record.isPresent()) owned(use, record.get());
            else wild(use, animal, moves, accessor);
            return true;
        }
        if (record.isEmpty()) return false;
        owned(use, record.get());
        return kind == RoleKind.ADOPT || kind == RoleKind.ADOPT_BABY;
    }

    private record Use(
        PlayerRef player,
        World world,
        UUID animalUuid,
        SpeciesDefinition species,
        RoleKind kind,
        @Nullable String itemId,
        boolean crouching,
        /** Admin test path: the food is not taken from the hand. */
        boolean free
    ) {
        UUID playerId() {
            return player.getUuid();
        }

        boolean baby() {
            return kind == RoleKind.WILD_BABY || kind == RoleKind.ADOPT_BABY || kind == RoleKind.RANCH_BABY;
        }
    }

    // ---- befriending wild animals ----

    private static void wild(@Nonnull Use use, @Nonnull Ref<EntityStore> animal, @Nullable MovementStates moves, @Nonnull ComponentAccessor<EntityStore> accessor) {
        Message animalName = AnimalControl.speciesName(use.species);
        CalmRules.FeedGate predator = PredatorApi.feedGate(use.species, use.animalUuid);
        if (predator == CalmRules.FeedGate.TOO_WILD) {
            // a wild predator takes no food from a hand until a Bait Bowl has calmed it; nothing else happens
            use.player.sendMessage(Message.translation("cozyranch_predator.too_wild").param("animal", animalName));
            return;
        }
        TamingRules.FoodKind food = TamingRules.foodKind(use.species, use.itemId);
        if (food == TamingRules.FoodKind.NONE) {
            use.player.sendMessage(Message.translation(use.itemId == null ? "cozyranch_taming.tame.hint" : "cozyranch_taming.tame.not_food").param("animal", animalName));
            return;
        }
        if (!gateOk(use)) return;
        boolean hurried = moves != null && (moves.sprinting || moves.running);
        if (TamingRules.startles(use.species.Shyness, use.crouching, hurried, ThreadLocalRandom.current().nextDouble())) {
            if (predator == CalmRules.FeedGate.CALM_PREDATOR) {
                UUID who = use.playerId();
                use.world.execute(() -> PredatorApi.startle(use.world, use.animalUuid, who));
                use.player.sendMessage(Message.translation("cozyranch_taming.tame.startled").param("animal", animalName));
                return;
            }
            use.world.execute(() -> {
                Ref<EntityStore> ref = use.world.getEntityStore().getRefFromUUID(use.animalUuid);
                if (ref != null && ref.isValid()) AnimalControl.startle(ref, use.world.getEntityStore().getStore());
            });
            use.player.sendMessage(Message.translation("cozyranch_taming.tame.startled").param("animal", animalName));
            return;
        }
        use.world.execute(() -> feedWild(use, food));
    }

    private static void feedWild(@Nonnull Use use, @Nonnull TamingRules.FoodKind food) {
        Store<EntityStore> store = use.world.getEntityStore().getStore();
        Ref<EntityStore> animal = use.world.getEntityStore().getRefFromUUID(use.animalUuid);
        Ref<EntityStore> playerEntity = use.player.getReference();
        if (animal == null || !animal.isValid() || playerEntity == null || !playerEntity.isValid()) return;
        if (Ranch.animals().byEntity(use.animalUuid).isPresent()) return;
        // checked again on the world thread: Calm may have ended (or the animal been startled) since the click
        if (PredatorApi.feedGate(use.species, use.animalUuid) == CalmRules.FeedGate.TOO_WILD) {
            use.player.sendMessage(Message.translation("cozyranch_predator.too_wild").param("animal", AnimalControl.speciesName(use.species)));
            return;
        }
        if (!use.free && !takeHeldItem(playerEntity, use.itemId, store)) return;
        if (use.species.Predator) PredatorApi.handFed(use.animalUuid, use.species);

        Message animalName = AnimalControl.speciesName(use.species);
        int needed = TamingRules.feedingsToTame(use.species);
        int total = PROGRESS.add(use.animalUuid, use.playerId(), TamingRules.tamePoints(food), System.currentTimeMillis());
        if (total < needed) {
            AnimalControl.cheer(animal, AnimalControl.HEARTS_SMALL, store);
            AnimalControl.sound(animal, RanchSounds.ANIMAL_HAPPY, store);
            RanchChat.tell(use.player, Message.translation("cozyranch_taming.tame.progress").param("animal", animalName).param("count", total).param("needed", needed));
            return;
        }
        PROGRESS.clear(use.animalUuid);
        if (!gateOk(use)) return;
        befriendNow(use, animal, store);
    }

    /** The wild animal trusts the player now: record, ranch role in Follow, events, cheer, name page. World thread. */
    private static void befriendNow(@Nonnull Use use, @Nonnull Ref<EntityStore> animal, @Nonnull Store<EntityStore> store) {
        Message animalName = AnimalControl.speciesName(use.species);
        NPCEntity npc = store.getComponent(animal, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null) return;
        Optional<String> ranchRole = AnimalControl.ranchRole(use.species, npc.getRoleName(), use.baby());
        if (ranchRole.isEmpty()) return;

        AnimalTameAttemptEvent attempt = RanchEvents.fire(new AnimalTameAttemptEvent(use.playerId(), use.animalUuid, use.species.Id, npc.getRoleName(), use.world.getName()));
        if (attempt.isCancelled()) {
            use.player.sendMessage(Message.translation("cozyranch_taming.tame.refused").param("animal", animalName));
            return;
        }
        AnimalRecord record = Ranch.animals().create(use.species.Id, use.playerId(), use.world.getName(), use.baby());
        record.following = true;
        record.traits = BreedingRules.tamedTraits(TraitRegistry.get(), ThreadLocalRandom.current());
        rememberPosition(record, animal, store);
        Ranch.animals().bindEntity(record, animal, use.animalUuid, store);
        PredatorApi.tamed(use.animalUuid);
        if (!AnimalControl.swapRole(animal, ranchRole.get(), AnimalControl.STATE_FOLLOW, store)) {
            Ranch.animals().remove(record.animalId);
            return;
        }
        Ranch.animals().markDirty(record);
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("Befriended %s %s (%s) as %s for %s", use.species.Id, use.animalUuid, npc.getRoleName(), ranchRole.get(), use.playerId());
        RanchEvents.fire(new AnimalTamedEvent(record.view(), use.playerId(), AnimalTamedEvent.Source.TAMED));
        AnimalControl.cheer(animal, AnimalControl.HEARTS, store);
        AnimalControl.sound(animal, RanchSounds.ANIMAL_TAMED, store);
        RanchChat.tell(use.player, Message.translation("cozyranch_taming.tame.done").param("animal", animalName));
        TamingApi.openNamePage(use.player, record.animalId);
    }

    /**
     * Admin test path ({@code /ranch admin befriend}): befriends the nearest supported wild animal within
     * {@code radius} blocks at once, through the same code as the last feeding. Returns the species id, or null.
     */
    @Nullable
    public static String debugBefriendNearest(@Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity, @Nonnull World world,
                                              @Nonnull Store<EntityStore> store, double radius) {
        TransformComponent pt = store.getComponent(playerEntity, TransformComponent.getComponentType());
        if (pt == null) return null;
        Vector3d p = pt.getPosition();
        Object[] best = {null, null, Double.MAX_VALUE};
        store.forEachChunk(NPCEntity.getComponentType(), (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                TransformComponent t = chunk.getComponent(i, TransformComponent.getComponentType());
                UUIDComponent id = chunk.getComponent(i, UUIDComponent.getComponentType());
                if (npc == null || t == null || id == null || npc.getRoleName() == null) continue;
                Optional<RoleMatch> m = SpeciesRegistry.byRole(npc.getRoleName());
                if (m.isEmpty() || (m.get().kind() != RoleKind.WILD && m.get().kind() != RoleKind.WILD_BABY)) continue;
                if (Ranch.animals().byEntity(id.getUuid()).isPresent()) continue;
                double d = t.getPosition().distance(p);
                if (d <= radius && d < (double) best[2]) {
                    best[0] = id.getUuid();
                    best[1] = m.get();
                    best[2] = d;
                }
            }
        });
        if (best[0] == null) return null;
        RoleMatch match = (RoleMatch) best[1];
        Use use = new Use(player, world, (UUID) best[0], match.species(), match.kind(), null, false, true);
        Ref<EntityStore> animal = world.getEntityStore().getRefFromUUID(use.animalUuid);
        if (animal == null || !animal.isValid()) return null;
        befriendNow(use, animal, store);
        return match.species().Id;
    }

    /**
     * Admin test path ({@code /ranch admin feed <item>}): offers {@code itemId} by hand to the nearest supported wild
     * animal within {@code radius} blocks through the same code as a Use with that item in hand (gates, predator Calm,
     * startle, progress, befriending), except that the item is not taken. Returns the species id, or null.
     */
    @Nullable
    public static String debugFeedNearest(@Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity, @Nonnull World world,
                                          @Nonnull Store<EntityStore> store, double radius, @Nonnull String itemId) {
        TransformComponent pt = store.getComponent(playerEntity, TransformComponent.getComponentType());
        if (pt == null) return null;
        Vector3d p = pt.getPosition();
        Object[] best = {null, null, Double.MAX_VALUE};
        store.forEachChunk(NPCEntity.getComponentType(), (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                TransformComponent t = chunk.getComponent(i, TransformComponent.getComponentType());
                UUIDComponent id = chunk.getComponent(i, UUIDComponent.getComponentType());
                if (npc == null || t == null || id == null || npc.getRoleName() == null) continue;
                Optional<RoleMatch> m = SpeciesRegistry.byRole(npc.getRoleName());
                if (m.isEmpty() || (m.get().kind() != RoleKind.WILD && m.get().kind() != RoleKind.WILD_BABY)) continue;
                if (Ranch.animals().byEntity(id.getUuid()).isPresent()) continue;
                double d = t.getPosition().distance(p);
                if (d <= radius && d < (double) best[2]) {
                    best[0] = id.getUuid();
                    best[1] = m.get();
                    best[2] = d;
                }
            }
        });
        if (best[0] == null) return null;
        RoleMatch match = (RoleMatch) best[1];
        Use use = new Use(player, world, (UUID) best[0], match.species(), match.kind(), itemId, false, true);
        Ref<EntityStore> animal = world.getEntityStore().getRefFromUUID(use.animalUuid);
        if (animal == null || !animal.isValid()) return null;
        wild(use, animal, null, store);
        return match.species().Id;
    }

    // ---- animals with a ranch record ----

    private static void owned(@Nonnull Use use, @Nonnull AnimalRecord record) {
        if (record.ownerId == null) {
            if (gateOk(use)) use.world.execute(() -> claim(use));
            return;
        }
        if (!record.ownerId.equals(use.playerId()) && !RanchPermissions.has(use.playerId(), RanchPermissions.ADMIN_MANAGE_ANY)) {
            use.player.sendMessage(Message.translation("cozyranch_taming.owned.not_yours").param("animal", AnimalControl.displayName(record)));
            return;
        }
        if (use.crouching) {
            if (!RanchPermissions.has(use.playerId(), RanchPermissions.FOLLOW)) {
                use.player.sendMessage(Message.translation("cozyranch_taming.follow.no_permission"));
                return;
            }
            use.world.execute(() -> toggleFollow(use));
            return;
        }
        TamingRules.FoodKind food = TamingRules.foodKind(use.species, use.itemId);
        if (food != TamingRules.FoodKind.NONE) use.world.execute(() -> feedOwned(use, food));
        else if (use.itemId == null) use.world.execute(() -> pet(use));
    }

    private static void claim(@Nonnull Use use) {
        Store<EntityStore> store = use.world.getEntityStore().getStore();
        Ref<EntityStore> animal = use.world.getEntityStore().getRefFromUUID(use.animalUuid);
        Optional<AnimalRecord> found = Ranch.animals().byEntity(use.animalUuid);
        if (animal == null || !animal.isValid() || found.isEmpty()) return;
        AnimalRecord record = found.get();
        if (record.ownerId != null) {
            use.player.sendMessage(Message.translation("cozyranch_taming.owned.not_yours").param("animal", AnimalControl.displayName(record)));
            return;
        }
        if (!gateOk(use)) return;
        if (use.kind == RoleKind.ADOPT || use.kind == RoleKind.ADOPT_BABY || use.kind == RoleKind.WILD || use.kind == RoleKind.WILD_BABY) {
            Optional<String> ranchRole = AnimalControl.ranchRole(use.species, AnimalControl.roleName(animal, store), record.baby);
            if (ranchRole.isEmpty() || !AnimalControl.swapRole(animal, ranchRole.get(), AnimalControl.STATE_FOLLOW, store)) return;
        }
        record.ownerId = use.playerId();
        record.following = true;
        rememberPosition(record, animal, store);
        Ranch.animals().markDirty(record);
        RanchEvents.fire(new AnimalOwnerChangedEvent(record.view(), null, use.playerId()));
        AnimalControl.cheer(animal, AnimalControl.HEARTS, store);
        AnimalControl.sound(animal, RanchSounds.ANIMAL_TAMED, store);
        RanchChat.tell(use.player, Message.translation("cozyranch_taming.claim.done").param("animal", AnimalControl.displayName(record)));
        refreshNameplate(record, animal, store);
        if (record.name == null || record.name.isBlank()) TamingApi.openNamePage(use.player, record.animalId);
    }

    private static void toggleFollow(@Nonnull Use use) {
        Store<EntityStore> store = use.world.getEntityStore().getStore();
        Ref<EntityStore> animal = use.world.getEntityStore().getRefFromUUID(use.animalUuid);
        Optional<AnimalRecord> found = Ranch.animals().byEntity(use.animalUuid);
        if (animal == null || !animal.isValid() || found.isEmpty()) return;
        setFollowing(found.get(), !found.get().following, animal, use.player, store);
    }

    /** Shared with the follow and stay commands. World thread, outside ticks. */
    static void setFollowing(@Nonnull AnimalRecord record, boolean following, @Nonnull Ref<EntityStore> animal, @Nonnull PlayerRef player, @Nonnull Store<EntityStore> store) {
        record.following = following;
        rememberPosition(record, animal, store);
        Ranch.animals().markDirty(record);
        FollowSystem.forget(record.animalId);
        Ref<EntityStore> owner = player.getReference();
        AnimalControl.applyFollow(animal, record, owner != null && owner.isValid() ? FollowAnchor.of(owner, store) : owner, store);
        RanchChat.tell(player, Message.translation(following ? "cozyranch_taming.follow.on" : "cozyranch_taming.follow.off").param("animal", AnimalControl.displayName(record)));
    }

    private static void feedOwned(@Nonnull Use use, @Nonnull TamingRules.FoodKind food) {
        Store<EntityStore> store = use.world.getEntityStore().getStore();
        Ref<EntityStore> animal = use.world.getEntityStore().getRefFromUUID(use.animalUuid);
        Ref<EntityStore> playerEntity = use.player.getReference();
        Optional<AnimalRecord> found = Ranch.animals().byEntity(use.animalUuid);
        if (animal == null || !animal.isValid() || playerEntity == null || !playerEntity.isValid() || found.isEmpty()) return;
        AnimalRecord record = found.get();
        long now = System.currentTimeMillis();
        if (!TamingRules.canFeedForFriendship(record.lastFedAtMs, now)) {
            use.player.sendMessage(Message.translation("cozyranch_taming.feed.full").param("animal", AnimalControl.displayName(record)));
            return;
        }
        if (!takeHeldItem(playerEntity, use.itemId, store)) return;
        record.lastFedAtMs = now;
        refreshNameplate(record, animal, store);
        CareApi.recordMeal(record, food);
        AnimalControl.sound(animal, addFriendship(record, TamingRules.feedFriendship(food)) ? RanchSounds.HEART_UP : RanchSounds.ANIMAL_HAPPY, store);
        AnimalControl.cheer(animal, food == TamingRules.FoodKind.FAVORITE ? AnimalControl.HEARTS : AnimalControl.HEARTS_SMALL, store);
        RanchChat.tell(use.player, Message.translation(food == TamingRules.FoodKind.FAVORITE ? "cozyranch_taming.feed.favorite" : "cozyranch_taming.feed.done")
            .param("animal", AnimalControl.displayName(record)).param("hearts", record.hearts).param("max", TamingRules.MAX_HEARTS));
    }

    private static void pet(@Nonnull Use use) {
        Store<EntityStore> store = use.world.getEntityStore().getStore();
        Ref<EntityStore> animal = use.world.getEntityStore().getRefFromUUID(use.animalUuid);
        Optional<AnimalRecord> found = Ranch.animals().byEntity(use.animalUuid);
        if (animal == null || !animal.isValid() || found.isEmpty()) return;
        AnimalRecord record = found.get();
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        long gameNow = TamingRules.gameClockMs(time.getGameTime());
        if (!TamingRules.canPetForFriendship(record.lastPettedAtMs, gameNow)) {
            // once a day: a second pet gives no hearts, sound, animation or friendship, only this line
            use.player.sendMessage(Message.translation("cozyranch_taming.pet.already").param("animal", AnimalControl.displayName(record)));
            return;
        }
        refreshNameplate(record, animal, store);
        AnimalControl.cheer(animal, AnimalControl.HEARTS_SMALL, store);
        record.lastPettedAtMs = gameNow;
        AnimalControl.sound(animal, addFriendship(record, TamingRules.PET_FRIENDSHIP) ? RanchSounds.HEART_UP : RanchSounds.ANIMAL_HAPPY, store);
        RanchChat.tell(use.player, Message.translation("cozyranch_taming.pet.done")
            .param("animal", AnimalControl.displayName(record)).param("hearts", record.hearts).param("max", TamingRules.MAX_HEARTS));
    }

    /** Brings the nameplate in line with the name and the config switch whenever the player deals with the animal. */
    private static void refreshNameplate(@Nonnull AnimalRecord record, @Nonnull Ref<EntityStore> animal, @Nonnull Store<EntityStore> store) {
        Nameplates.apply(record, animal, store);
    }

    /** @return true when a heart was gained */
    private static boolean addFriendship(@Nonnull AnimalRecord record, float amount) {
        amount *= com.hexvane.cozytalesranching.care.CareApi.friendshipMultiplier(record);
        int before = record.hearts;
        TamingRules.Friendship next = TamingRules.addFriendship(record.hearts, record.heartProgress, amount);
        record.hearts = next.hearts();
        record.heartProgress = next.progress();
        Ranch.animals().markDirty(record);
        if (next.hearts() != before) RanchEvents.fire(new AnimalFriendshipChangedEvent(record.view(), before, next.hearts()));
        return next.hearts() > before;
    }

    // ---- shared checks ----

    private static boolean gateOk(@Nonnull Use use) {
        UUID id = use.playerId();
        TamingRules.Gate gate = TamingRules.tameGate(
            RanchConfig.get().isTamingBlocked(use.world.getName()),
            RanchPermissions.has(id, RanchPermissions.ADMIN_BYPASS_BLOCKED_WORLDS),
            RanchPermissions.has(id, RanchPermissions.TAME),
            Ranch.animals().ownedBy(id).size(),
            RanchConfig.get().maxAnimalsPerPlayer()
        );
        String key = switch (gate) {
            case OK -> null;
            case BLOCKED_WORLD -> "cozyranch_taming.gate.blocked_world";
            case NO_PERMISSION -> "cozyranch_taming.gate.no_permission";
            case AT_LIMIT -> "cozyranch_taming.gate.at_limit";
        };
        if (key == null) return true;
        use.player.sendMessage(Message.translation(key).param("max", RanchConfig.get().maxAnimalsPerPlayer()));
        return false;
    }

    /** Takes one of the item only if the player still holds it; the hand may have changed since the click. */
    private static boolean takeHeldItem(@Nonnull Ref<EntityStore> playerEntity, @Nullable String itemId, @Nonnull Store<EntityStore> store) {
        if (itemId == null) return false;
        ItemStack inHand = InventoryComponent.getItemInHand(store, playerEntity);
        if (ItemStack.isEmpty(inHand) || !itemId.equals(inHand.getItemId())) return false;
        InventoryHelper.removeItemInHand(playerEntity, store, 1);
        return true;
    }

    static void rememberPosition(@Nonnull AnimalRecord record, @Nonnull Ref<EntityStore> animal, @Nonnull ComponentAccessor<EntityStore> accessor) {
        TransformComponent t = accessor.getComponent(animal, TransformComponent.getComponentType());
        if (t == null) return;
        Vector3d p = t.getPosition();
        record.lastX = p.x;
        record.lastY = p.y;
        record.lastZ = p.z;
    }

    @Nullable
    private static MovementStates movement(@Nonnull Ref<EntityStore> entity, @Nonnull ComponentAccessor<EntityStore> accessor) {
        MovementStatesComponent c = accessor.getComponent(entity, MovementStatesComponent.getComponentType());
        return c != null ? c.getMovementStates() : null;
    }
}
