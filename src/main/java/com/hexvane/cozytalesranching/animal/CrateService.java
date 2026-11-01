package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.breeding.TraitRegistry;
import com.hexvane.cozytalesranching.houseui.HouseView;
import com.hexvane.cozytalesranching.prefs.RanchChat;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.metadata.CapturedNPCMetadata;
import com.hypixel.hytale.server.npc.role.Role;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * A capture crate that really holds a ranch animal: the record goes {@code CRATED} with no entity (recovery leaves it
 * alone), the crate names the record, and opening the crate makes the record's next incarnation. Also sends each crate's
 * tooltip line to the players, as a translation of the crate's own role key ({@link CrateRules#roleKey}).
 */
final class CrateService {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private CrateService() {}

    /** The record of a ranch animal's current entity, or null for a wild or stale one. */
    @Nullable
    static AnimalRecord recordOf(@Nonnull AnimalMarker marker, @Nonnull UUID entityUuid) {
        PersistentAnimalRegistry registry = AnimalFeature.registry();
        if (registry == null || marker.animalId() == null) return null;
        if (!registry.isCurrent(marker.animalId(), entityUuid, marker.generation())) return null;
        return registry.store().get(marker.animalId());
    }

    static boolean mayCapture(@Nonnull PlayerRef player, @Nonnull AnimalRecord r) {
        return CrateRules.mayCapture(player.getUuid(), r.ownerId, RanchPermissions.has(player.getUuid(), RanchPermissions.ADMIN_MANAGE_ANY));
    }

    /**
     * Puts this ranch animal in the empty crate in this slot, whatever its size: any animal with a record goes in, so vanilla's
     * list of small animals does not apply. The record goes first, so the entity's removal, which the caller queues right
     * after this, does not read as a lost animal.
     */
    static void capture(@Nonnull World world, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull PlayerRef player, @Nonnull AnimalRecord r, @Nonnull Ref<EntityStore> target,
                        @Nonnull ItemContainer inventory, short slot, @Nonnull ItemStack crate, @Nullable String fullIcon) {
        PersistentAnimalRegistry registry = AnimalFeature.registry();
        if (registry == null) return;
        CapturedNPCMetadata captured = new CapturedNPCMetadata();
        captured.setFullItemIcon(fullIcon);
        NPCEntity npc = accessor.getComponent(target, NPCEntity.getComponentType());
        Role role = npc == null ? null : npc.getRole();
        ModelAsset look = role == null ? null : ModelAsset.getAssetMap().getAsset(role.getAppearanceName());
        if (look != null && look.getIcon() != null) {
            captured.setIconPath(look.getIcon());
            captured.setFullItemIcon(look.getIcon());
        }
        long generation = registry.store().crate(r);
        registry.scheduleSave();
        captured.setNpcNameKey(CrateRules.roleKey(r.animalId));
        ItemStack holding = crate.withMetadata(CapturedNPCMetadata.KEYED_CODEC, captured)
            .withMetadata(CrateMetadata.KEYED_CODEC, new CrateMetadata(r.animalId, generation));
        inventory.replaceItemStackInSlot(slot, crate, holding);
        LOG.info("Ranch animal " + r.animalId + " (" + r.speciesId + ") went into a capture crate held by " + player.getUsername());
        world.execute(() -> {
            moveOut(player, r);
            for (PlayerRef online : Universe.get().getPlayers()) send(online, List.of(r));
            RanchChat.tell(player, Message.translation("cozyranch_animal.crate.captured").param("animal", name(r, player.getLanguage())));
        });
    }

    /** A crated animal lives nowhere: its place in the house is free, and a lost crate cannot hold a place forever. */
    private static void moveOut(@Nonnull PlayerRef player, @Nonnull AnimalRecord r) {
        if (r.houseId == null) return;
        if (Ranch.houseService().unassign(player, r) != com.hexvane.cozytalesranching.house.HouseService.Result.OK) {
            r.houseId = null;
            Ranch.animals().markDirty(r);
        }
    }

    /**
     * Opens the crate in this hotbar slot near {@code near}. World thread, outside a tick. A crate that no longer holds its
     * record (a copy, or the animal was brought back by an admin) just goes empty; with no room nearby it stays full.
     */
    static void release(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> holder, @Nonnull PlayerRef player, short slot, @Nonnull Vector3d near) {
        PersistentAnimalRegistry registry = AnimalFeature.registry();
        AnimalRecovery recovery = AnimalFeature.recovery();
        if (registry == null || recovery == null || !holder.isValid()) return;
        InventoryComponent.Hotbar hotbar = store.getComponent(holder, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return;
        ItemContainer inventory = hotbar.getInventory();
        ItemStack crate = inventory.getItemStack(slot);
        CrateMetadata held = crate == null ? null : crate.getFromMetadataOrNull(CrateMetadata.KEY, CrateMetadata.CODEC);
        if (held == null || held.animalId == null) return;
        AnimalRecord r = registry.store().get(held.animalId);
        if (r == null || !CrateRules.holds(r.state, r.generation, held.generation)) {
            inventory.replaceItemStackInSlot(slot, crate, crate.withMetadata(null));
            player.sendMessage(Message.translation("cozyranch_animal.crate.stale"));
            return;
        }
        if (!recovery.respawn(world, store, r, near)) {
            player.sendMessage(Message.translation("cozyranch_animal.crate.no_room"));
            return;
        }
        inventory.replaceItemStackInSlot(slot, crate, crate.withMetadata(null));
        RanchChat.tell(player, Message.translation("cozyranch_animal.crate.released").param("animal", name(r, player.getLanguage())));
    }

    /** A player has joined: the tooltip of any crate they may come across. Text is theirs alone, in their language. */
    static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        PersistentAnimalRegistry registry = AnimalFeature.registry();
        Ref<EntityStore> ref = event.getPlayerRef();
        if (registry == null || !ref.isValid()) return;
        PlayerRef player = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
        if (player != null) send(player, registry.store().crated());
    }

    private static void send(@Nonnull PlayerRef player, @Nonnull List<AnimalRecord> crated) {
        if (crated.isEmpty()) return;
        Map<String, String> lines = new HashMap<>();
        for (AnimalRecord r : crated) lines.put(CrateRules.translationKey(r.animalId), tooltip(r, player.getLanguage()));
        player.getPacketHandler().writeNoCache(new UpdateTranslations(UpdateType.AddOrUpdate, lines));
    }

    /** What the crate's tooltip says about the animal: {@code Clover the Chicken, 3 hearts, Happy, Lucky, Hardy}. */
    @Nonnull
    static String tooltip(@Nonnull AnimalRecord r, @Nullable String language) {
        int hearts = Math.clamp(r.hearts, 0, HouseView.MAX_HEARTS);
        String mood = text(language, "cozyranch_houseui.mood." + HouseView.mood(r.happiness).name().toLowerCase(Locale.ROOT));
        List<String> traits = new ArrayList<>();
        if (r.traits != null) {
            for (String id : r.traits) {
                if (id == null || id.isBlank()) continue;
                String key = TraitRegistry.get().get(id).map(d -> d.NameKey).orElse(null);
                traits.add(key == null || key.isBlank() ? id : text(language, key));
            }
        }
        return CrateRules.fill(text(language, "cozyranch_animal.crate.tooltip"), Map.of(
            "animal", name(r, language),
            "hearts", hearts == 1 ? text(language, "cozyranch_animal.crate.heart") : CrateRules.fill(text(language, "cozyranch_animal.crate.hearts"), Map.of("count", String.valueOf(hearts))),
            "mood", mood,
            "traits", CrateRules.traitList(traits)));
    }

    /** {@code Clover the Chicken}, {@code Chicken} or {@code Baby Chicken}. */
    @Nonnull
    private static String name(@Nonnull AnimalRecord r, @Nullable String language) {
        String key = AnimalNames.key(r);
        if (key == null) key = SpeciesRegistry.byId(r.speciesId).map(s -> s.NameKey).orElse(null);
        String species = key == null || key.isBlank() ? r.speciesId : text(language, key);
        if (r.baby) species = CrateRules.fill(text(language, "cozyranch_animal.crate.baby"), Map.of("species", species));
        if (r.name == null || r.name.isBlank()) return species;
        return CrateRules.fill(text(language, "cozyranch_animal.crate.named"), Map.of("name", r.name, "species", species));
    }

    @Nonnull
    private static String text(@Nullable String language, @Nonnull String key) {
        String found = I18nModule.get().getMessage(language, key);
        return found != null ? found : key;
    }
}
