package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.spawning.ISpawnableWithModel;
import com.hypixel.hytale.server.spawning.SpawningContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps a ranch animal's skin (the random attachments of its model, which skin packs such as Celly's Wildlife Skins
 * add to vanilla wild models) through taming, role swaps, reloads, growing up, the house display and release. The
 * rules are in {@link SkinRules}; this class reads model assets and entities for them. Mod-agnostic: nothing names a
 * skin pack, and with none installed vanilla's own random attachments (cow udder, sheep wool, camel and mosshorn
 * looks) are kept the same way. World thread only.
 */
public final class AnimalSkins {
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    /** How long a released animal's skin waits for its wild model to come back. */
    private static final long RELEASE_WAIT_MS = 120_000L;

    /** Released animals by entity UUID: the skin to put back on once the role swap gives them their wild model. */
    private static final Map<UUID, Pending> RELEASED = new ConcurrentHashMap<>();

    private record Pending(@Nonnull SkinRules.Skin skin, @Nullable String ranchModel, long untilMs) {}

    private AnimalSkins() {}

    // ---- records ----

    /**
     * Notes the skin the entity wears now on a record that has none yet: a wild animal being befriended or claimed, an
     * adopted animal, or an animal tamed before this feature (whose look is then kept from now on). Returns true when
     * the record changed (the caller saves it).
     */
    public static boolean capture(@Nonnull AnimalRecord r, @Nonnull Ref<EntityStore> entity, @Nonnull ComponentAccessor<EntityStore> accessor) {
        if (r.skinModel != null || !entity.isValid()) return false;
        ModelComponent mc = accessor.getComponent(entity, ModelComponent.getComponentType());
        Model model = mc == null ? null : mc.getModel();
        if (model == null || model.getModelAssetId() == null) return false;
        Map<String, String> ids = model.getRandomAttachmentIds();
        if (ids == null || ids.isEmpty()) return false;
        r.skinModel = model.getModelAssetId();
        r.skinIds = new LinkedHashMap<>(ids);
        debug("Skin of " + r.animalId + " noted from " + r.skinModel + ": " + r.skinIds);
        return true;
    }

    /** A bred baby's skin: a matching parent's (mixed when both match), else one rolled from the wild model of its look. */
    public static void giveBabySkin(@Nonnull AnimalRecord baby, @Nonnull SpeciesDefinition species, @Nonnull AnimalRecord a, @Nullable String roleA,
                                    @Nonnull AnimalRecord b, @Nullable String roleB, @Nullable String babyAdultRole,
                                    @Nonnull java.util.random.RandomGenerator rng) {
        SkinRules.Skin skin = SkinRules.inherit(SkinRules.of(a.skinModel, a.skinIds), roleA, SkinRules.of(b.skinModel, b.skinIds), roleB, babyAdultRole, rng);
        if (skin == null && babyAdultRole != null && !babyAdultRole.isEmpty()) skin = rollWild(species, babyAdultRole);
        if (skin == null) return;
        baby.skinModel = skin.source();
        baby.skinIds = new LinkedHashMap<>(skin.ids());
    }

    /** A fresh roll from the wild model of an adult ranch role's look, or null when that model has no random sets. */
    @Nullable
    static SkinRules.Skin rollWild(@Nonnull SpeciesDefinition species, @Nonnull String adultRanchRole) {
        String wildRole = species.wildRoleFor(adultRanchRole, false);
        if (wildRole == null || wildRole.isEmpty()) return null;
        String modelId = roleModelId(wildRole);
        ModelAsset asset = modelId == null ? null : ModelAsset.getAssetMap().getAsset(modelId);
        if (asset == null) return null;
        Map<String, String> ids = asset.generateRandomAttachmentIds();
        return SkinRules.of(modelId, ids == null ? null : new LinkedHashMap<>(ids));
    }

    /** The model asset id a role spawns with (its appearance), or null. */
    @Nullable
    public static String roleModelId(@Nonnull String role) {
        NPCPlugin npcs = NPCPlugin.get();
        if (npcs == null) return null;
        int index = npcs.getIndex(role);
        if (index < 0) return null;
        if (!(npcs.tryGetCachedValidRole(index) instanceof ISpawnableWithModel spawnable)) return null;
        SpawningContext ctx = new SpawningContext();
        try {
            if (!ctx.setSpawnable(spawnable) || ctx.getModel() == null) return null;
            return ctx.getModel().getModelAssetId();
        } finally {
            ctx.releaseFull();
        }
    }

    // ---- entities ----

    /**
     * Brings the entity's model in line with the record's skin and lets the record learn changes made in place (see
     * {@link SkinRules#mergeIds}). {@code fresh}: the entity was just made or changed model asset. Puts a new model
     * component only when what is drawn or carried differs, so calling it again is free. Returns true when the record
     * changed (the caller saves it).
     */
    public static boolean apply(@Nonnull AnimalRecord r, @Nonnull Ref<EntityStore> entity, @Nonnull ComponentAccessor<EntityStore> accessor, boolean fresh) {
        if (r.skinIds == null || r.skinIds.isEmpty() || !entity.isValid()) return false;
        ModelComponent mc = accessor.getComponent(entity, ModelComponent.getComponentType());
        Model current = mc == null ? null : mc.getModel();
        if (current == null) return false;
        ModelAsset target = ModelAsset.getAssetMap().getAsset(current.getModelAssetId());
        if (target == null) return false;
        SkinRules.AssetView targetView = view(target);
        Map<String, String> ids = SkinRules.mergeIds(current.getRandomAttachmentIds(), r.skinIds, targetView.sets().keySet(), fresh);
        boolean recordChanged = false;
        if (!SkinRules.sameIds(ids, r.skinIds)) {
            r.skinIds = new LinkedHashMap<>(ids);
            recordChanged = true;
        }
        Model wanted = withSkin(current, targetView, sourceView(r.skinModel), ids);
        if (wanted != null) {
            accessor.putComponent(entity, ModelComponent.getComponentType(), new ModelComponent(wanted));
            debug("Skin of " + r.animalId + " applied on " + current.getModelAssetId() + (fresh ? " (fresh)" : "") + ": " + ids);
        }
        return recordChanged;
    }

    /** A model for the record built on {@code asset} at {@code scale}, wearing its skin (house display). */
    @Nonnull
    public static Model modelFor(@Nonnull ModelAsset asset, float scale, @Nullable AnimalRecord r) {
        if (r == null || r.skinIds == null || r.skinIds.isEmpty()) return Model.createScaledModel(asset, scale);
        Model base = Model.createScaledModel(asset, scale);
        SkinRules.AssetView targetView = view(asset);
        Map<String, String> ids = SkinRules.mergeIds(base.getRandomAttachmentIds(), r.skinIds, targetView.sets().keySet(), true);
        Model wanted = withSkin(base, targetView, sourceView(r.skinModel), ids);
        return wanted != null ? wanted : base;
    }

    /** A short text naming the record's skin, so a display shown with an old skin is noticed and made again. */
    @Nonnull
    public static String key(@Nullable AnimalRecord r) {
        return r == null || r.skinIds == null || r.skinIds.isEmpty() ? "" : r.skinModel + r.skinIds;
    }

    /**
     * Before a release: the entity gets this skin back once the role swap has given it its wild model (any model other
     * than the ranch one it wears now).
     */
    public static void rememberRelease(@Nonnull UUID entityUuid, @Nonnull AnimalRecord r, @Nonnull Ref<EntityStore> entity,
                                       @Nonnull ComponentAccessor<EntityStore> accessor) {
        SkinRules.Skin skin = SkinRules.of(r.skinModel, r.skinIds);
        long now = System.currentTimeMillis();
        RELEASED.values().removeIf(p -> p.untilMs() < now);
        if (skin == null) return;
        ModelComponent mc = entity.isValid() ? accessor.getComponent(entity, ModelComponent.getComponentType()) : null;
        String ranchModel = mc == null || mc.getModel() == null ? null : mc.getModel().getModelAssetId();
        RELEASED.put(entityUuid, new Pending(new SkinRules.Skin(skin.source(), new LinkedHashMap<>(skin.ids())), ranchModel, now + RELEASE_WAIT_MS));
    }

    /** True when released skins are waiting (cheap check before a lookup). */
    static boolean anyReleased() {
        return !RELEASED.isEmpty();
    }

    /**
     * A released animal whose model just changed: once its model is no longer the ranch one, puts its skin back.
     * Returns true when it handled the entity.
     */
    static boolean applyReleased(@Nonnull UUID entityUuid, @Nonnull Ref<EntityStore> entity, @Nonnull ComponentAccessor<EntityStore> accessor) {
        Pending p = RELEASED.get(entityUuid);
        if (p == null) return false;
        if (p.untilMs() < System.currentTimeMillis()) {
            RELEASED.remove(entityUuid, p);
            return false;
        }
        ModelComponent mc = accessor.getComponent(entity, ModelComponent.getComponentType());
        Model current = mc == null ? null : mc.getModel();
        if (current == null) return false;
        String roleModel = current.getModelAssetId();
        if (roleModel == null || roleModel.equals(p.ranchModel())) return false;
        RELEASED.remove(entityUuid, p);
        ModelAsset target = ModelAsset.getAssetMap().getAsset(roleModel);
        if (target == null) return true;
        SkinRules.AssetView targetView = view(target);
        Map<String, String> ids = SkinRules.mergeIds(current.getRandomAttachmentIds(), p.skin().ids(), targetView.sets().keySet(), true);
        Model wanted = withSkin(current, targetView, sourceView(p.skin().source()), ids);
        if (wanted != null) accessor.putComponent(entity, ModelComponent.getComponentType(), new ModelComponent(wanted));
        debug("Released animal " + entityUuid + " got its skin back on " + roleModel + ": " + ids);
        return true;
    }

    /** The model {@code current} with {@code ids} drawn, or null when it already is. */
    @Nullable
    private static Model withSkin(@Nonnull Model current, @Nonnull SkinRules.AssetView target, @Nullable SkinRules.AssetView source,
                                  @Nonnull Map<String, String> ids) {
        SkinRules.Result result = SkinRules.resolve(target, source, ids);
        List<SkinRules.Piece> now = pieces(current.getAttachments());
        if (SkinRules.samePieces(now, result.pieces()) && SkinRules.sameIds(current.getRandomAttachmentIds(), result.ids())) return null;
        ModelAttachment[] attachments = new ModelAttachment[result.pieces().size()];
        for (int i = 0; i < attachments.length; i++) {
            SkinRules.Piece p = result.pieces().get(i);
            attachments[i] = new ModelAttachment(p.model(), p.texture(), p.gradientSet(), p.gradientId(), 1.0);
        }
        return new Model(current.getModelAssetId(), current.getScale(), new LinkedHashMap<>(result.ids()), attachments, current.getBoundingBox(),
            current.getModel(), current.getTexture(), current.getGradientSet(), current.getGradientId(), current.getEyeHeight(),
            current.getCrouchOffset(), current.getSittingOffset(), current.getSleepingOffset(), current.getAnimationSetMap(),
            current.getCamera(), current.getLight(), current.getParticles(), current.getTrails(), current.getPhysicsValues(),
            current.getDetailBoxes(), current.getPhobia(), current.getPhobiaModelAssetId());
    }

    @Nullable
    private static SkinRules.AssetView sourceView(@Nullable String modelId) {
        if (modelId == null || modelId.isEmpty()) return null;
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelId);
        return asset == null ? null : view(asset);
    }

    @Nonnull
    static SkinRules.AssetView view(@Nonnull ModelAsset asset) {
        Map<String, Map<String, SkinRules.Piece>> sets = new LinkedHashMap<>();
        Map<String, Map<String, ModelAttachment>> raw = asset.getRandomAttachmentSets();
        if (raw != null) {
            for (Map.Entry<String, Map<String, ModelAttachment>> e : raw.entrySet()) {
                Map<String, SkinRules.Piece> set = new LinkedHashMap<>();
                if (e.getValue() != null) e.getValue().forEach((id, a) -> set.put(id, piece(a)));
                sets.put(e.getKey(), set);
            }
        }
        return new SkinRules.AssetView(asset.getId(), asset.getModel(), pieces(asset.getDefaultAttachments()), sets);
    }

    @Nonnull
    private static List<SkinRules.Piece> pieces(@Nullable ModelAttachment[] attachments) {
        List<SkinRules.Piece> out = new ArrayList<>();
        if (attachments == null) return out;
        for (ModelAttachment a : attachments) {
            if (a != null) out.add(piece(a));
        }
        return out;
    }

    @Nonnull
    private static SkinRules.Piece piece(@Nullable ModelAttachment a) {
        if (a == null) return new SkinRules.Piece(null, null, null, null);
        return new SkinRules.Piece(a.getModel(), a.getTexture(), a.getGradientSet(), a.getGradientId());
    }

    private static void debug(@Nonnull String msg) {
        if (RanchConfig.get().debugLogging()) LOG.info(msg);
    }
}
