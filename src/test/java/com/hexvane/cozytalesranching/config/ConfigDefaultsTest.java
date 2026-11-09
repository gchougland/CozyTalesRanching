package com.hexvane.cozytalesranching.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigDefaultsTest {
    @Test
    void onlyKeysTheFileLacksAreReportedInCodecOrder() {
        Set<String> written = new LinkedHashSet<>(List.of("A", "B", "AllowRaiding", "C"));
        assertEquals(List.of("AllowRaiding"), ConfigDefaults.missingKeys(written, Set.of("A", "B", "C")));
        assertEquals(List.of(), ConfigDefaults.missingKeys(written, Set.of("A", "B", "AllowRaiding", "C", "OldRemovedSetting")));
        assertEquals(List.of("A", "B", "AllowRaiding", "C"), ConfigDefaults.missingKeys(written, Set.of()));
    }
}
