package com.hexvane.cozytalesranching.animal;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.UUID;

/**
 * On a capture crate that holds a ranch animal: which record it holds and at which generation. Capturing and every new
 * incarnation bump the generation, so a copied or outdated crate no longer matches and can never make a second animal.
 */
final class CrateMetadata {
    static final String KEY = "CozyRanchAnimal";
    static final BuilderCodec<CrateMetadata> CODEC = BuilderCodec.builder(CrateMetadata.class, CrateMetadata::new)
        .append(new KeyedCodec<>("AnimalId", Codec.UUID_STRING), (m, v) -> m.animalId = v, m -> m.animalId).add()
        .append(new KeyedCodec<>("Generation", Codec.LONG), (m, v) -> m.generation = v, m -> m.generation).add()
        .build();
    static final KeyedCodec<CrateMetadata> KEYED_CODEC = new KeyedCodec<>(KEY, CODEC);

    UUID animalId;
    long generation;

    CrateMetadata() {}

    CrateMetadata(UUID animalId, long generation) {
        this.animalId = animalId;
        this.generation = generation;
    }
}
