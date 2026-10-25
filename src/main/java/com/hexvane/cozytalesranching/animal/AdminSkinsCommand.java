package com.hexvane.cozytalesranching.animal;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@code /ranch admin skins [--radius=16] [--set=Base=2001+Eyes=2002] [--release]}: for every animal of a ranch species nearby, the skin its model wears (model
 * asset, random attachment ids, the attachment textures drawn) and the skin its ranch record keeps. For testing skin
 * packs without a client. {@code --set} first gives every wild animal (no ranch record) of a ranch species nearby those
 * random attachment ids on its own model asset, as a skin pack's roll or a role action would, so a test can start from
 * a known skin. {@code --release} releases every ranch animal nearby instead, as {@code /ranch release} does for the one
 * looked at, to test that a released animal keeps its skin. One sphere query when run.
 */
final class AdminSkinsCommand extends AbstractPlayerCommand {
    private final OptionalArg<Double> radiusArg;
    private final OptionalArg<String> setArg;
    private final FlagArg releaseArg;

    AdminSkinsCommand() {
        super("skins", "cozyranch_animal.commands.admin.skins.desc");
        this.requirePermission(RanchPermissions.ADMIN_INSPECT);
        this.radiusArg = this.withOptionalArg("radius", "cozyranch_animal.commands.admin.skins.arg.radius", ArgTypes.DOUBLE);
        this.setArg = this.withOptionalArg("set", "cozyranch_animal.commands.admin.skins.arg.set", ArgTypes.STRING);
        this.releaseArg = this.withFlagArg("release", "cozyranch_animal.commands.admin.skins.arg.release");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        TransformComponent self = store.getComponent(ref, TransformComponent.getComponentType());
        if (self == null) return;
        double radius = context.provided(radiusArg) ? Math.clamp(context.get(radiusArg), 1.0, 64.0) : 16.0;
        ComponentType<EntityStore, AnimalMarker> markerType = AnimalMarker.type();
        Map<String, String> set = context.provided(setArg) ? parse(context.get(setArg)) : Map.of();
        boolean release = Boolean.TRUE.equals(releaseArg.get(context));
        int shown = 0;
        for (Ref<EntityStore> e : new ArrayList<>(TargetUtil.getAllEntitiesInSphere(self.getPosition(), radius, store))) {
            if (e == null || !e.isValid()) continue;
            NPCEntity npc = store.getComponent(e, NPCEntity.getComponentType());
            ModelComponent mc = store.getComponent(e, ModelComponent.getComponentType());
            if (npc == null || npc.getRoleName() == null || mc == null || mc.getModel() == null) continue;
            if (SpeciesRegistry.byRole(npc.getRoleName()).isEmpty()) continue;
            Model model = mc.getModel();
            AnimalMarker marker = markerType == null ? null : store.getComponent(e, markerType);
            UUIDComponent uuid = store.getComponent(e, UUIDComponent.getComponentType());
            AnimalRecord r = marker != null && marker.animalId() != null ? Ranch.animals().get(marker.animalId()).orElse(null)
                : uuid == null ? null : Ranch.animals().byEntity(uuid.getUuid()).orElse(null);
            if (r != null && release) {
                // as /ranch release does for the animal looked at (a test player cannot aim): the record goes, the role swap follows
                com.hexvane.cozytalesranching.taming.TamingApi.release(r, e, playerRef, store);
                context.sendMessage(Message.translation("cozyranch_animal.admin.skins.released").param("role", npc.getRoleName()));
                shown++;
                continue;
            }
            if (r == null && !set.isEmpty()) {
                ModelAsset asset = ModelAsset.getAssetMap().getAsset(model.getModelAssetId());
                if (asset != null) {
                    Map<String, String> ids = new LinkedHashMap<>(model.getRandomAttachmentIds() == null ? Map.of() : model.getRandomAttachmentIds());
                    ids.putAll(set);
                    model = Model.createScaledModel(asset, model.getScale(), ids);
                    store.putComponent(e, ModelComponent.getComponentType(), new ModelComponent(model));
                }
            }
            context.sendMessage(Message.translation("cozyranch_animal.admin.skins.line")
                .param("role", npc.getRoleName())
                .param("model", String.valueOf(model.getModelAssetId()))
                .param("ids", ids(model.getRandomAttachmentIds()))
                .param("count", model.getAttachments() == null ? 0 : model.getAttachments().length)
                .param("textures", textures(model.getAttachments()))
                .param("record", r == null ? "-" : r.skinIds == null ? "none" : r.skinModel + " " + ids(r.skinIds)));
            shown++;
        }
        if (shown == 0) context.sendMessage(Message.translation("cozyranch_animal.admin.skins.none"));
    }

    @Nonnull
    private static Map<String, String> parse(@Nonnull String text) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String part : text.split("[,+;]")) {
            int eq = part.indexOf('=');
            if (eq <= 0 || eq == part.length() - 1) continue;
            out.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        return out;
    }

    @Nonnull
    static String ids(@Nullable Map<String, String> ids) {
        return ids == null || ids.isEmpty() ? "{}" : new TreeMap<>(ids).toString();
    }

    @Nonnull
    private static String textures(@Nullable ModelAttachment[] attachments) {
        if (attachments == null || attachments.length == 0) return "-";
        List<String> out = new ArrayList<>();
        for (ModelAttachment a : attachments) {
            if (a == null) continue;
            out.add(file(a.getModel()) + ":" + file(a.getTexture()));
        }
        return String.join(",", out);
    }

    @Nonnull
    private static String file(@Nullable String path) {
        if (path == null) return "?";
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
