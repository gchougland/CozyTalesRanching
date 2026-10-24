package com.hexvane.cozytalesranching.species;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * Lets each bundled species carry its own part of the shared feature files. Next to {@code Species/<Name>.json} a
 * species may ship {@code Species/<Name>/<Feature>.json} (Behaviour, Habitat, Journal, Poop, Wildlife): the same shape
 * as the shared file, holding only that species' entries. It is merged into the bundled file before parsing: objects
 * merge key by key, arrays are appended, anything else is replaced. A server's own override file in the data folder
 * is read as it is.
 */
public final class SpeciesOverlay {
    private static final String DIR = "/Server/CozyTalesRanching/Species/";
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");

    private SpeciesOverlay() {}

    /** The bundled feature file with every bundled species' fragment merged in. */
    @Nonnull
    public static Reader merged(@Nonnull InputStream bundled, @Nonnull String feature) throws IOException {
        JsonElement root = JsonParser.parseReader(new InputStreamReader(bundled, StandardCharsets.UTF_8));
        for (String name : SpeciesRegistry.BUNDLED) {
            try (InputStream in = SpeciesOverlay.class.getResourceAsStream(DIR + name + "/" + feature + ".json")) {
                if (in == null) continue;
                root = merge(root, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read species fragment " + name + "/" + feature + ".json", e);
            }
        }
        return new StringReader(root.toString());
    }

    /** {@code add} on top of {@code base}: objects merge key by key, arrays append, other values are replaced. */
    @Nonnull
    static JsonElement merge(@Nonnull JsonElement base, @Nonnull JsonElement add) {
        if (base.isJsonObject() && add.isJsonObject()) {
            JsonObject out = base.getAsJsonObject().deepCopy();
            for (Map.Entry<String, JsonElement> e : add.getAsJsonObject().entrySet()) {
                JsonElement had = out.get(e.getKey());
                out.add(e.getKey(), had == null ? e.getValue() : merge(had, e.getValue()));
            }
            return out;
        }
        if (base.isJsonArray() && add.isJsonArray()) {
            JsonArray out = base.getAsJsonArray().deepCopy();
            out.addAll(add.getAsJsonArray());
            return out;
        }
        return add;
    }
}
