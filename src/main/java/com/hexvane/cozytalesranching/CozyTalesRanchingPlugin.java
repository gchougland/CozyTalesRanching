package com.hexvane.cozytalesranching;

import com.hexvane.cozytalesranching.animal.AnimalFeature;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.command.RanchCommand;
import com.hexvane.cozytalesranching.config.RanchConfig;
import com.hexvane.cozytalesranching.generated.HstatsBuildMetadata;
import com.hexvane.cozytalesranching.house.HouseFeature;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.taming.TamingFeature;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.AssetPackRegisterEvent;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.Config;
import java.nio.file.Files;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class CozyTalesRanchingPlugin extends JavaPlugin {
    private static CozyTalesRanchingPlugin instance;

    @Nonnull
    private final Config<RanchConfig> config = withConfig("RanchConfig", RanchConfig.CODEC);

    public CozyTalesRanchingPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Nullable
    public static CozyTalesRanchingPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        instance = this;
        RanchConfig.bind(config.get());

        String hstatsModUuid = HstatsBuildMetadata.HSTATS_MOD_UUID;
        String modVersion = this.getManifest().getVersion().toString();
        if (!hstatsModUuid.isBlank()) {
            new HStats(hstatsModUuid, modVersion);
        }

        RanchPermissions.register();
        SpeciesRegistry.load(getDataDirectory());

        AnimalFeature.register(this);
        TamingFeature.register(this);
        HouseFeature.register(this);
        com.hexvane.cozytalesranching.houseui.HouseUiFeature.register(this);
        com.hexvane.cozytalesranching.care.CareFeature.register(this);
        com.hexvane.cozytalesranching.breeding.BreedingFeature.register(this);

        this.getCommandRegistry().registerCommand(new RanchCommand());
        getLogger().atInfo().log("Cozy Tales: Ranching v%s ready.", modVersion);
    }

    @Override
    protected void start() {
        if (!Files.exists(getDataDirectory().resolve("RanchConfig.json"))) {
            config.save();
            getLogger().atInfo().log("Created default RanchConfig.json in %s", getDataDirectory());
        }

        if (!this.getManifest().includesAssetPack()) {
            return;
        }
        String packId = new PluginIdentifier(this.getManifest()).toString();
        AssetPack pack = AssetModule.get().getAssetPack(packId);
        if (pack == null) {
            getLogger().atWarning().log("Asset pack %s not found in AssetModule; Asset Editor may not list this mod", packId);
            return;
        }
        HytaleServer.get()
            .getEventBus()
            .<Void, AssetPackRegisterEvent>dispatchFor(AssetPackRegisterEvent.class)
            .dispatch(new AssetPackRegisterEvent(pack));
        CommonAssetModule commonAssets = CommonAssetModule.get();
        if (commonAssets != null) {
            commonAssets.loadCommonAssets(pack, System.nanoTime());
            if (Universe.get().getPlayerCount() > 0) {
                Universe.get().broadcastPacketNoCache(new RequestCommonAssetsRebuild());
            }
        }
    }

    /** Re-reads RanchConfig.json and the species files. */
    public void reload() {
        config.load().join();
        RanchConfig.bind(config.get());
        SpeciesRegistry.load(getDataDirectory());
        getLogger().atInfo().log("Reloaded RanchConfig.json and species.");
    }
}
