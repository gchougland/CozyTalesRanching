package com.hexvane.cozytalesranching.house;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * One placed animal house. Saved with Gson; field names are the format. Change a field, then
 * {@link HouseRegistry#markDirty}. Residents are the animals whose {@code AnimalRecord.houseId} is this id.
 */
public final class HouseRecord {
    public UUID houseId;
    public UUID ownerId;
    public String speciesId = "";
    /** Block type id of the house, e.g. {@code CozyRanch_House_CowBarn}. */
    public String blockId = "";
    public String worldName = "";
    /** The block position the player placed (the furniture's origin block). */
    public int x;
    public int y;
    public int z;
    /** Block rotation index as placed (0 to 3). */
    public int rotation;
    /** Where animals stand to go in and come out: just outside the door, on the ground. */
    public double doorX;
    public double doorY;
    public double doorZ;
    public int capacity = 4;
    @Nullable public String name;
    public long placedAtMs;
    /** In-game day number of the last morning handled, so a morning runs once per day. */
    public long lastMorningDay = -1;
    /** The house page's breeding toggle. Written by the house service, read by breeding. */
    public boolean breedingEnabled = true;
    /** Habitat around the house, written by care at most every HabitatScanIntervalSeconds. */
    public boolean habitatRequirementsMet;
    /** 0..1: required features present plus the share of preferred features present. */
    public float habitatScore;
    /** Habitat feature ids (species HabitatRequires/HabitatPrefers) not found near the house. */
    public List<String> habitatMissing = new ArrayList<>();
    public long habitatScannedAtMs;
}
