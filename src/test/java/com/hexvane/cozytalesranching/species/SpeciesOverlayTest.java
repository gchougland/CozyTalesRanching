package com.hexvane.cozytalesranching.species;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class SpeciesOverlayTest {
    private static JsonElement j(String s) {
        return JsonParser.parseString(s);
    }

    @Test
    void objectsMergeArraysAppendValuesReplace() {
        JsonElement base = j("{\"Species\":{\"cow\":{\"a\":1}},\"List\":[1],\"Rate\":2}");
        JsonElement add = j("{\"Species\":{\"goat\":{\"a\":3}},\"List\":[2],\"Rate\":5}");
        assertEquals(j("{\"Species\":{\"cow\":{\"a\":1},\"goat\":{\"a\":3}},\"List\":[1,2],\"Rate\":5}"), SpeciesOverlay.merge(base, add));
    }

    @Test
    void theBaseIsNotChanged() {
        JsonElement base = j("{\"Species\":{\"cow\":{\"a\":1}}}");
        SpeciesOverlay.merge(base, j("{\"Species\":{\"duck\":{}}}"));
        assertEquals(j("{\"Species\":{\"cow\":{\"a\":1}}}"), base);
    }
}
