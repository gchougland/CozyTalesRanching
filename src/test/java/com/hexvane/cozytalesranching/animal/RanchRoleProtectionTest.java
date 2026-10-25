package com.hexvane.cozytalesranching.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.cozytalesranching.animal.ProtectionRules.Source;
import com.hexvane.cozytalesranching.compat.CompatGuard;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every ranch animal is protected. The protection system acts on any NPC with the ranch marker whose role the species
 * data knows as a ranch role (adult or baby, every look and rare look); the rules then cancel every hit while the
 * animal is owned. So: every role file under Roles/CozyRanch must be a known ranch role, every ranch role named in
 * species data must have a role file, and the rules must cancel players, creatures and the world alike.
 */
class RanchRoleProtectionTest {
    private static final Path ROLES = Path.of("src/main/resources/Server/NPC/Roles/CozyRanch");
    @TempDir static Path data;

    @BeforeAll
    static void load() {
        SpeciesRegistry.load(data);
    }

    @Test
    void everyRanchRoleFileIsAManagedRanchRole() throws IOException {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.walk(ROLES)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject role = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (role.has("Type") && "Abstract".equals(role.get("Type").getAsString())) continue;
                String id = f.getFileName().toString().replace(".json", "");
                checked++;
                RoleKind kind = SpeciesRegistry.byRole(id).map(SpeciesRegistry.RoleMatch::kind).orElse(null);
                if (kind != RoleKind.RANCH && kind != RoleKind.RANCH_BABY) bad.add(id + " (" + kind + ")");
                else if (!CompatGuard.isManageableRole(id)) bad.add(id + " (ignored)");
            }
        }
        assertTrue(checked > 100, "found the role files (" + checked + ")");
        assertEquals(List.of(), bad, "ranch roles the protection would not cover");
    }

    @Test
    void everyRanchRoleInSpeciesDataHasARoleFile() {
        List<String> missing = new ArrayList<>();
        for (SpeciesDefinition s : SpeciesRegistry.all()) {
            List<String> roles = new ArrayList<>();
            if (!s.RanchRole.isEmpty()) roles.add(s.RanchRole);
            if (!s.RanchBabyRole.isEmpty()) roles.add(s.RanchBabyRole);
            roles.addAll(s.RanchRoleVariants.values());
            roles.addAll(s.RanchBabyRoleVariants.values());
            for (String r : roles) {
                if (!Files.exists(ROLES.resolve(r + ".json")) && !Files.exists(ROLES.resolve("Rare").resolve(r + ".json"))) missing.add(s.Id + ": " + r);
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void ownedAnimalsTakeNoDamageFromAnything() {
        for (Source s : Source.values()) {
            assertTrue(ProtectionRules.cancels(true, s, true, true), s + " is cancelled");
        }
    }

    @Test
    void theSwitchesStillWork() {
        assertFalse(ProtectionRules.cancels(true, Source.PLAYER, false, true));
        assertFalse(ProtectionRules.cancels(true, Source.WORLD, false, true));
        assertTrue(ProtectionRules.cancels(true, Source.CREATURE, false, true));
        assertFalse(ProtectionRules.cancels(true, Source.CREATURE, true, false));
        assertTrue(ProtectionRules.cancels(true, Source.PLAYER, true, false));
    }

    @Test
    void unownedAnimalsAreLeftToVanilla() {
        for (Source s : Source.values()) assertFalse(ProtectionRules.cancels(false, s, true, true));
    }
}
