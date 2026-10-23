package com.hexvane.cozytalesranching;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Sound event ids the mod plays from Java. A missing sound event is silently skipped. */
public final class RanchSounds {
    public static final String ANIMAL_HAPPY = "SFX_CozyRanch_Animal_Happy";
    public static final String ANIMAL_TAMED = "SFX_CozyRanch_Animal_Tamed";
    public static final String HEART_UP = "SFX_CozyRanch_Heart_Up";
    public static final String BABY_BORN = "SFX_CozyRanch_Baby_Born";
    public static final String HOUSE_DOOR = "SFX_CozyRanch_House_Door";
    public static final String TROUGH_FILL = "SFX_CozyRanch_Trough_Fill";
    public static final String PRODUCE_COLLECT = "SFX_CozyRanch_Produce_Collect";
    public static final String JOURNAL_OPEN = "SFX_CozyRanch_Journal_Open";
    public static final String JOURNAL_DISCOVERY = "SFX_CozyRanch_Journal_Discovery";
    public static final String VISITOR_ARRIVED = "SFX_CozyRanch_Visitor_Arrived";
    public static final String TOY_PLAY = "SFX_CozyRanch_Toy_Play";
    public static final String BUTCHER = "SFX_CozyRanch_Butcher";

    private RanchSounds() {}

    /** World thread. Plays a positional sound to players in range. */
    public static void play(@Nonnull String soundEventId, double x, double y, double z, @Nonnull ComponentAccessor<EntityStore> store) {
        int index = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (index == SoundEvent.EMPTY_ID || index < 0) {
            return;
        }
        SoundUtil.playSoundEvent3d(index, SoundCategory.SFX, x, y, z, store);
    }
}
