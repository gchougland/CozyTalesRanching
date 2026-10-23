package com.hexvane.cozytalesranching.rehome;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.RanchSounds;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.api.AnimalView;
import com.hexvane.cozytalesranching.api.event.AnimalDiedEvent;
import com.hexvane.cozytalesranching.api.event.RanchEvents;
import com.hexvane.cozytalesranching.breeding.TraitBook;
import com.hexvane.cozytalesranching.breeding.TraitRegistry;
import com.hexvane.cozytalesranching.compat.CompatGuard;
import com.hexvane.cozytalesranching.produce.ProduceRules;
import com.hexvane.cozytalesranching.rehome.RehomeRules.Verdict;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * The Butcher's Block. Using it (or {@code /ranch butcher} beside it) processes the nearest adult animal that follows
 * the player: a soft poof, the entity and its record gone, and the species' vanilla drop list rolled several times
 * into the player's inventory. Named animals and animals with four or more hearts need a second use within ten
 * seconds. World thread, outside system ticks.
 */
final class ButcherService {
    static final String BLOCK_ID = "CozyRanch_ButcherBlock";
    /** How far from the block a following animal may stand. */
    static final double REACH = 8.0;
    /** How far {@code /ranch butcher} looks for a block around the player. */
    static final int COMMAND_BLOCK_RADIUS = 5;
    static final String POOF_PARTICLES = "Block_Land_Hard_Dust";

    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final ButcherRules.Confirmations CONFIRMATIONS = new ButcherRules.Confirmations();

    private record Candidate(AnimalRecord record, Ref<EntityStore> ref, Vector3d pos, double distanceSq) {}

    private ButcherService() {}

    /** The block within {@link #COMMAND_BLOCK_RADIUS} of the player, as its centre; null when there is none. Command only. */
    @Nullable
    static Vector3d findBlockNear(@Nonnull World world, @Nonnull Vector3d at) {
        int id = BlockType.getAssetMap().getIndex(BLOCK_ID);
        if (id < 0 || id == BlockType.EMPTY_ID) return null;
        SectionReader reader = new SectionReader(world.getChunkStore());
        int bx = (int) Math.floor(at.x), by = (int) Math.floor(at.y), bz = (int) Math.floor(at.z);
        Vector3d best = null;
        double bestD = Double.MAX_VALUE;
        int r = COMMAND_BLOCK_RADIUS;
        for (int x = bx - r; x <= bx + r; x++) {
            for (int y = by - 2; y <= by + 2; y++) {
                for (int z = bz - r; z <= bz + r; z++) {
                    if (!reader.hasStorageInMemory(x, y, z) || reader.getBlock(x, y, z) != id) continue;
                    Vector3d c = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
                    double d = c.distanceSquared(at);
                    if (d < bestD) {
                        bestD = d;
                        best = c;
                    }
                }
            }
        }
        return best;
    }

    static void process(@Nonnull World world, @Nonnull PlayerRef player, @Nonnull Ref<EntityStore> playerEntity, @Nonnull Vector3d block) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        UUID pid = player.getUuid();
        if (!RanchPermissions.has(pid, RanchPermissions.BUTCHER)) {
            player.sendMessage(Message.translation("cozyranch_rehome.butcher.no_permission"));
            return;
        }
        Candidate pick = null;
        boolean sawBaby = false;
        for (AnimalRecord r : Ranch.animals().ownedBy(pid)) {
            if (!r.following || r.state != AnimalRecord.State.ACTIVE) continue;
            Ref<EntityStore> ref = RehomeAnimals.live(world, r);
            Vector3d pos = ref == null ? null : RehomeAnimals.position(ref, store);
            if (pos == null) continue;
            double d = pos.distanceSquared(block);
            if (d > REACH * REACH) continue;
            if (r.baby) {
                sawBaby = true;
                continue;
            }
            if (pick == null || d < pick.distanceSq()) pick = new Candidate(r, ref, pos, d);
        }
        if (pick == null) {
            player.sendMessage(Message.translation(sawBaby ? "cozyranch_rehome.butcher.baby" : "cozyranch_rehome.butcher.none"));
            return;
        }
        AnimalRecord record = pick.record();
        Message animal = RehomeAnimals.displayName(record);
        Verdict v = RehomeRules.checkButcher(pid, record.ownerId, record.baby, true);
        NPCEntity npc = store.getComponent(pick.ref(), NPCEntity.getComponentType());
        if (v != Verdict.OK || npc == null || npc.getRole() == null || npc.getRoleName() == null
            || !CompatGuard.isManageable(pick.ref(), store, npc.getRoleName())) {
            player.sendMessage(Message.translation("cozyranch_rehome.butcher.refused").param("animal", animal));
            return;
        }
        long now = System.currentTimeMillis();
        if (ButcherRules.needsConfirm(record.name, record.hearts)) {
            if (!CONFIRMATIONS.confirmOrArm(pid, record.animalId, now)) {
                LOG.info("Butcher: " + RehomeAnimals.describe(record) + " (hearts " + record.hearts + ") needs confirmation from " + player.getUsername());
                player.sendMessage(Message.translation(record.name != null && !record.name.isBlank()
                        ? "cozyranch_rehome.butcher.confirm_named" : "cozyranch_rehome.butcher.confirm_hearts")
                    .param("animal", animal).param("hearts", record.hearts).param("seconds", ButcherRules.CONFIRM_WINDOW_MS / 1000));
                return;
            }
        } else {
            CONFIRMATIONS.clear(pid);
        }

        TraitBook traits = TraitRegistry.get();
        double factor = ButcherRules.rollFactor(record.happiness, record.hearts, traits.multiplier(record.traits, ProduceRules.PRODUCTION_MULTIPLIER));
        int rolls = ButcherRules.rolls(factor, traits.chance(record.traits, ProduceRules.BONUS_PRODUCT_CHANCE), ThreadLocalRandom.current());
        String dropList = npc.getRole().getDropListId();
        Map<String, Integer> items = roll(dropList, rolls);
        if (items.isEmpty()) {
            player.sendMessage(Message.translation("cozyranch_rehome.butcher.nothing").param("animal", animal));
            return;
        }

        AnimalView view = record.view();
        Ranch.animals().remove(record.animalId);
        if (pick.ref().isValid()) store.removeEntity(pick.ref(), RemoveReason.REMOVE);
        Vector3d p = pick.pos();
        ParticleUtil.spawnParticleEffect(POOF_PARTICLES, new Vector3d(p).add(0, 0.5, 0), store);
        RanchSounds.play(RanchSounds.BUTCHER, block.x, block.y, block.z, store);
        int total = give(playerEntity, store, items);
        LOG.info(String.format(java.util.Locale.ROOT, "Butchered %s for %s at the block %.1f %.1f %.1f: happiness %.2f, hearts %d, traits %s, factor %.2f, %d roll(s) of %s -> %s",
            RehomeAnimals.describe(record), player.getUsername(), block.x, block.y, block.z, record.happiness, record.hearts, record.traits, factor, rolls, dropList, items));
        player.sendMessage(Message.translation("cozyranch_rehome.butcher.done").param("animal", animal).param("count", total));
        RanchEvents.fire(new AnimalDiedEvent(view, AnimalDiedEvent.Cause.BUTCHERED, pid));
    }

    @Nonnull
    private static Map<String, Integer> roll(@Nullable String dropList, int rolls) {
        Map<String, Integer> out = new LinkedHashMap<>();
        ItemModule module = ItemModule.get();
        if (dropList == null || dropList.isEmpty() || module == null) return out;
        for (int i = 0; i < rolls; i++) {
            for (ItemStack s : module.getRandomItemDrops(dropList)) {
                if (s == null || s.isEmpty()) continue;
                out.merge(s.getItemId(), s.getQuantity(), Integer::sum);
            }
        }
        return out;
    }

    /** Into the inventory a stack at a time; what does not fit drops at the player's feet. Returns the item count. */
    private static int give(@Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store, @Nonnull Map<String, Integer> items) {
        List<ItemStack> spill = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            Item item = Item.getAssetMap().getAsset(e.getKey());
            int max = item == null ? 1 : Math.max(1, item.getMaxStack());
            for (int left = e.getValue(); left > 0; left -= max) {
                int n = Math.min(max, left);
                total += n;
                ItemStack rest = Player.giveItem(new ItemStack(e.getKey(), n), playerEntity, store).getRemainder();
                if (rest != null && !rest.isEmpty()) spill.add(rest);
            }
            Player.notifyPickupItem(playerEntity, new ItemStack(e.getKey(), e.getValue()), null, store);
        }
        Vector3d at = RehomeAnimals.position(playerEntity, store);
        if (!spill.isEmpty() && at != null) {
            Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(store, spill, at.add(0, 0.5, 0), Rotation3f.IDENTITY);
            store.addEntities(drops, AddReason.SPAWN);
        }
        return total;
    }
}
